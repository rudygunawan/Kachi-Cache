package com.github.rudygunawan.kachi;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.CacheStrategy;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.policy.EvictionPolicy;
import com.github.rudygunawan.kachi.policy.Policy;
import com.github.rudygunawan.kachi.time.FakeTicker;
import com.github.rudygunawan.kachi.time.Ticker;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Caffeine compatibility features:
 * - Ticker interface for testing
 * - estimatedSize() method
 * - Policy snapshot queries (hottest, coldest, youngest, oldest)
 */
public class CaffeineCompatibilityTest {

    @Test
    void testTickerInterfaceSystemTicker() {
        // System ticker should work like System.nanoTime()
        Ticker ticker = Ticker.systemTicker();

        long time1 = ticker.read();
        long time2 = ticker.read();

        assertTrue(time2 >= time1, "Ticker should return monotonically increasing values");
        assertNotNull(ticker.toString());
    }

    @Test
    void testFakeTickerForTesting() throws InterruptedException {
        FakeTicker ticker = new FakeTicker();

        // Initially at zero
        assertEquals(0, ticker.read());
        assertEquals(0, ticker.getNanos());

        // Advance by 1 minute
        ticker.advance(1, TimeUnit.MINUTES);
        assertEquals(TimeUnit.MINUTES.toNanos(1), ticker.read());

        // Advance by 500 nanoseconds
        ticker.advance(500);
        assertEquals(TimeUnit.MINUTES.toNanos(1) + 500, ticker.read());

        // Negative advances are ignored
        ticker.advance(-1000);
        assertEquals(TimeUnit.MINUTES.toNanos(1) + 500, ticker.read());

        // Can set to specific value
        ticker.setNanos(12345);
        assertEquals(12345, ticker.read());

        // Method chaining works
        long result = ticker.advance(100).advance(200).read();
        assertEquals(12645, result);
    }

    @Test
    void testExpirationWithFakeTicker() {
        FakeTicker ticker = new FakeTicker();

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .ticker(ticker)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();

        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // Immediately available
        assertEquals("value1", cache.getIfPresent("key1"));
        assertEquals("value2", cache.getIfPresent("key2"));

        // Advance time by 5 minutes - still valid
        ticker.advance(5, TimeUnit.MINUTES);
        assertEquals("value1", cache.getIfPresent("key1"));
        assertEquals("value2", cache.getIfPresent("key2"));

        // Advance time by another 6 minutes (total 11 minutes) - should expire
        ticker.advance(6, TimeUnit.MINUTES);
        cache.cleanUp(); // Trigger cleanup

        assertNull(cache.getIfPresent("key1"));
        assertNull(cache.getIfPresent("key2"));
    }

    @Test
    void testEstimatedSizeMethod() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .build();

        // Both methods should return the same value
        assertEquals(0, cache.size());
        assertEquals(0, cache.estimatedSize());

        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        assertEquals(3, cache.size());
        assertEquals(3, cache.estimatedSize());
        assertEquals(cache.size(), cache.estimatedSize());

        cache.invalidate("key2");

        assertEquals(2, cache.size());
        assertEquals(2, cache.estimatedSize());
    }

    @Test
    void testPolicySnapshotQueriesWithEviction() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .strategy(CacheStrategy.PRECISION)
                .maximumSize(100)
                .evictionPolicy(EvictionPolicy.LRU)
                .build();

        // Add some entries with delays to ensure different timestamps
        for (int i = 1; i <= 10; i++) {
            cache.put("key" + i, "value" + i);
            try {
                Thread.sleep(10); // Small delay to ensure different write times
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Test hottest() - should return newest entries first
        Map<String, String> hottest = cache.policy().eviction().orElseThrow().hottest(5);
        assertEquals(5, hottest.size());
        assertTrue(hottest.containsKey("key10")); // Newest should be in hottest
        assertTrue(hottest.containsKey("key9"));

        // Test coldest() - should return oldest entries first
        Map<String, String> coldest = cache.policy().eviction().orElseThrow().coldest(5);
        assertEquals(5, coldest.size());
        assertTrue(coldest.containsKey("key1")); // Oldest should be in coldest
        assertTrue(coldest.containsKey("key2"));

        // Test with limit larger than cache size
        Map<String, String> all = cache.policy().eviction().orElseThrow().hottest(100);
        assertEquals(10, all.size());

        // Test with zero limit
        Map<String, String> none = cache.policy().eviction().orElseThrow().hottest(0);
        assertEquals(0, none.size());
    }

    @Test
    void testPolicySnapshotQueriesWithExpiration() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();

        // Add some entries with delays
        for (int i = 1; i <= 10; i++) {
            cache.put("key" + i, "value" + i);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Test youngest() - should return newest entries
        Map<String, String> youngest = cache.policy().expiration().orElseThrow().youngest(5);
        assertEquals(5, youngest.size());
        assertTrue(youngest.containsKey("key10")); // Newest
        assertTrue(youngest.containsKey("key9"));

        // Test oldest() - should return oldest entries
        Map<String, String> oldest = cache.policy().expiration().orElseThrow().oldest(5);
        assertEquals(5, oldest.size());
        assertTrue(oldest.containsKey("key1")); // Oldest
        assertTrue(oldest.containsKey("key2"));

        // Verify ordering in oldest - first entry should be the oldest
        String firstKey = oldest.keySet().iterator().next();
        assertEquals("key1", firstKey);
    }

    @Test
    void testPolicySnapshotQueriesEdgeCases() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .build();

        // Empty cache
        Map<String, String> empty = cache.policy().eviction().orElseThrow().hottest(10);
        assertEquals(0, empty.size());

        // Single entry
        cache.put("only", "value");
        Map<String, String> single = cache.policy().eviction().orElseThrow().hottest(10);
        assertEquals(1, single.size());
        assertTrue(single.containsKey("only"));

        // Negative limit should throw
        assertThrows(IllegalArgumentException.class, () ->
            cache.policy().eviction().orElseThrow().hottest(-1)
        );
        assertThrows(IllegalArgumentException.class, () ->
            cache.policy().eviction().orElseThrow().coldest(-1)
        );
    }

    @Test
    void testPolicySnapshotQueriesNoCachePolicy() {
        // Cache without size limit - no eviction policy
        Cache<String, String> cache = CacheBuilder.newBuilder().build();

        assertFalse(cache.policy().eviction().isPresent());

        // Cache without expiration - no expiration policy
        Cache<String, String> cache2 = CacheBuilder.newBuilder()
                .maximumSize(100)
                .build();

        assertFalse(cache2.policy().expiration().isPresent());
    }

    @Test
    void testPolicySnapshotQueriesWithDifferentEvictionPolicies() {
        // Test with FIFO
        Cache<String, String> fifoCache = CacheBuilder.newBuilder()
                .strategy(CacheStrategy.PRECISION)
                .maximumSize(100)
                .evictionPolicy(EvictionPolicy.FIFO)
                .build();

        for (int i = 1; i <= 5; i++) {
            fifoCache.put("key" + i, "value" + i);
        }

        Map<String, String> fifoHottest = fifoCache.policy().eviction().orElseThrow().hottest(3);
        assertEquals(3, fifoHottest.size());

        // Test with LFU
        Cache<String, String> lfuCache = CacheBuilder.newBuilder()
                .strategy(CacheStrategy.PRECISION)
                .maximumSize(100)
                .evictionPolicy(EvictionPolicy.LFU)
                .build();

        // Add entries and access them different numbers of times
        lfuCache.put("key1", "value1");
        lfuCache.put("key2", "value2");
        lfuCache.put("key3", "value3");

        // Access key3 multiple times to make it "hot"
        for (int i = 0; i < 10; i++) {
            lfuCache.getIfPresent("key3");
        }

        // Access key2 a few times
        for (int i = 0; i < 3; i++) {
            lfuCache.getIfPresent("key2");
        }

        Map<String, String> lfuHottest = lfuCache.policy().eviction().orElseThrow().hottest(2);
        assertEquals(2, lfuHottest.size());
        assertTrue(lfuHottest.containsKey("key3")); // Most frequently accessed
    }

    @Test
    void testPolicySnapshotQueriesReturnUnmodifiableMap() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .build();

        cache.put("key1", "value1");

        Map<String, String> hottest = cache.policy().eviction().orElseThrow().hottest(10);

        // Should not be able to modify the returned map
        assertThrows(UnsupportedOperationException.class, () ->
            hottest.put("key2", "value2")
        );

        assertThrows(UnsupportedOperationException.class, () ->
            hottest.remove("key1")
        );
    }

    @Test
    void testHighPerformanceStrategySnapshotQueries() {
        // HIGH_PERFORMANCE strategy uses write time as approximation
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .strategy(CacheStrategy.HIGH_PERFORMANCE)
                .maximumSize(100)
                .build();

        for (int i = 1; i <= 10; i++) {
            cache.put("key" + i, "value" + i);
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Should still work, using write time as approximation
        Map<String, String> hottest = cache.policy().eviction().orElseThrow().hottest(5);
        assertEquals(5, hottest.size());

        Map<String, String> coldest = cache.policy().eviction().orElseThrow().coldest(5);
        assertEquals(5, coldest.size());
    }

    @Test
    void testTickerWithRefreshPolicy() {
        FakeTicker ticker = new FakeTicker();

        // This test just verifies ticker is accepted in builder
        // Full refresh integration would need more complex setup
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .ticker(ticker)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();

        cache.put("key", "value");
        assertEquals("value", cache.getIfPresent("key"));

        // Verify ticker was used (through age query)
        long age = cache.policy().expiration().orElseThrow().ageOf("key");
        assertTrue(age >= 0);
    }
}
