package com.github.rudygunawan.kachi;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.CacheLoader;
import com.github.rudygunawan.kachi.api.LoadingCache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.model.CacheStats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CacheTest {

    @Test
    void testBasicCacheOperations() {
        Cache<String, String> cache = CacheBuilder.newBuilder().build();

        // Test put and get
        cache.put("key1", "value1");
        assertEquals("value1", cache.getIfPresent("key1"));

        // Test null for non-existent key
        assertNull(cache.getIfPresent("nonexistent"));

        // Test size
        cache.put("key2", "value2");
        assertEquals(2, cache.size());

        // Test invalidate
        cache.invalidate("key1");
        assertNull(cache.getIfPresent("key1"));
        assertEquals(1, cache.size());

        // Test invalidateAll
        cache.invalidateAll();
        assertEquals(0, cache.size());
    }

    @Test
    void testPutAll() {
        Cache<String, Integer> cache = CacheBuilder.newBuilder().build();

        Map<String, Integer> data = new HashMap<>();
        data.put("one", 1);
        data.put("two", 2);
        data.put("three", 3);

        cache.putAll(data);

        assertEquals(3, cache.size());
        assertEquals(1, cache.getIfPresent("one"));
        assertEquals(2, cache.getIfPresent("two"));
        assertEquals(3, cache.getIfPresent("three"));
    }

    @Test
    void testGetWithCallable() throws Exception {
        Cache<String, String> cache = CacheBuilder.newBuilder().build();

        String value = cache.get("key", () -> "computed");
        assertEquals("computed", value);
        assertEquals("computed", cache.getIfPresent("key"));

        // Should return cached value, not recompute
        String value2 = cache.get("key", () -> "recomputed");
        assertEquals("computed", value2);
    }

    @Test
    void testExpireAfterWrite() throws Exception {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(100, TimeUnit.MILLISECONDS)
                .build();

        cache.put("key", "value");
        assertEquals("value", cache.getIfPresent("key"));

        // Wait for expiration
        Thread.sleep(150);

        assertNull(cache.getIfPresent("key"));
    }

    @Test
    void testExpireAfterAccess() throws Exception {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .expireAfterAccess(100, TimeUnit.MILLISECONDS)
                .build();

        cache.put("key", "value");
        assertEquals("value", cache.getIfPresent("key"));

        // Access within expiration window
        Thread.sleep(50);
        assertEquals("value", cache.getIfPresent("key"));

        // Access again within expiration window
        Thread.sleep(50);
        assertEquals("value", cache.getIfPresent("key"));

        // Wait for expiration without access
        Thread.sleep(150);
        assertNull(cache.getIfPresent("key"));
    }

    @Test
    void testMaximumSize() {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(3)
                .build();

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");
        assertEquals(3, cache.size());

        // Adding 4th element should evict least recently used (1)
        cache.put(4, "four");
        assertEquals(3, cache.size());
        assertNull(cache.getIfPresent(1));
        assertEquals("four", cache.getIfPresent(4));
    }

    @Test
    void testLRUEviction() {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(3)
                .build();

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        // Access 1 to make it recently used
        cache.getIfPresent(1);

        // Adding 4 should evict 2 (least recently used)
        cache.put(4, "four");

        assertNotNull(cache.getIfPresent(1));
        assertNull(cache.getIfPresent(2));
        assertNotNull(cache.getIfPresent(3));
        assertNotNull(cache.getIfPresent(4));
    }

    @Test
    void testCacheLoader() throws Exception {
        AtomicInteger loadCount = new AtomicInteger(0);

        CacheLoader<String, String> loader = new CacheLoader<String, String>() {
            @Override
            public String load(String key) {
                loadCount.incrementAndGet();
                return "loaded_" + key;
            }
        };

        LoadingCache<String, String> cache = CacheBuilder.newBuilder().build(loader);

        // First access should load
        String value1 = cache.get("key1");
        assertEquals("loaded_key1", value1);
        assertEquals(1, loadCount.get());

        // Second access should use cached value
        String value2 = cache.get("key1");
        assertEquals("loaded_key1", value2);
        assertEquals(1, loadCount.get());

        // Different key should load again
        String value3 = cache.get("key2");
        assertEquals("loaded_key2", value3);
        assertEquals(2, loadCount.get());
    }

    @Test
    void testCacheLoaderWithException() {
        CacheLoader<String, String> loader = new CacheLoader<String, String>() {
            @Override
            public String load(String key) throws Exception {
                throw new Exception("Load failed");
            }
        };

        LoadingCache<String, String> cache = CacheBuilder.newBuilder().build(loader);

        assertThrows(Exception.class, () -> cache.get("key"));
    }

    @Test
    void testGetAll() throws Exception {
        CacheLoader<Integer, String> loader = new CacheLoader<Integer, String>() {
            @Override
            public String load(Integer key) {
                return "value_" + key;
            }
        };

        LoadingCache<Integer, String> cache = CacheBuilder.newBuilder().build(loader);

        // Pre-populate one value
        cache.put(1, "cached_1");

        List<Integer> keys = Arrays.asList(1, 2, 3);
        Map<Integer, String> results = cache.getAll(keys);

        assertEquals(3, results.size());
        assertEquals("cached_1", results.get(1));
        assertEquals("value_2", results.get(2));
        assertEquals("value_3", results.get(3));
    }

    @Test
    void testRecordStats() throws Exception {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .recordStats()
                .build();

        // Misses
        cache.getIfPresent("key1");
        cache.getIfPresent("key2");

        // Hits
        cache.put("key1", "value1");
        cache.getIfPresent("key1");
        cache.getIfPresent("key1");

        CacheStats stats = cache.stats();
        assertEquals(2, stats.hitCount());
        assertEquals(2, stats.missCount());
        assertEquals(4, stats.requestCount());
        assertEquals(0.5, stats.hitRate(), 0.01);
    }

    @Test
    void testStatsWithLoader() throws Exception {
        CacheLoader<String, String> loader = new CacheLoader<String, String>() {
            @Override
            public String load(String key) {
                return "loaded_" + key;
            }
        };

        LoadingCache<String, String> cache = CacheBuilder.newBuilder()
                .recordStats()
                .build(loader);

        cache.get("key1");
        cache.get("key1");
        cache.get("key2");

        CacheStats stats = cache.stats();
        assertEquals(2, stats.hitCount());
        assertEquals(2, stats.missCount());
        assertEquals(2, stats.loadSuccessCount());
        assertEquals(0, stats.loadFailureCount());
    }

    @Test
    void testAsMap() {
        Cache<String, Integer> cache = CacheBuilder.newBuilder().build();

        cache.put("one", 1);
        cache.put("two", 2);
        cache.put("three", 3);

        Map<String, Integer> map = cache.asMap();
        assertEquals(3, map.size());
        assertEquals(1, map.get("one"));
        assertEquals(2, map.get("two"));
        assertEquals(3, map.get("three"));
    }

    @Test
    void testCleanUp() throws Exception {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(50, TimeUnit.MILLISECONDS)
                .build();

        cache.put("key1", "value1");
        cache.put("key2", "value2");
        assertEquals(2, cache.size());

        Thread.sleep(100);

        // Expired entries should still be counted until cleanup
        cache.cleanUp();
        assertEquals(0, cache.size());
    }

    @Test
    @Timeout(5)
    void testConcurrentAccess() throws Exception {
        AtomicInteger loadCount = new AtomicInteger(0);

        CacheLoader<Integer, String> loader = new CacheLoader<Integer, String>() {
            @Override
            public String load(Integer key) throws Exception {
                loadCount.incrementAndGet();
                Thread.sleep(10); // Simulate expensive operation
                return "value_" + key;
            }
        };

        LoadingCache<Integer, String> cache = CacheBuilder.newBuilder()
                .recordStats()
                .build(loader);

        int numThreads = 10;
        int numOperations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < numOperations; j++) {
                        int key = j % 10; // Reuse keys to test caching
                        String value = cache.get(key);
                        assertNotNull(value);
                        assertTrue(value.startsWith("value_"));
                    }
                } catch (Exception e) {
                    fail("Exception in concurrent access: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Each unique key should be loaded only once despite concurrent access
        assertTrue(loadCount.get() <= 10, "Load count should be at most 10, was: " + loadCount.get());

        CacheStats stats = cache.stats();
        assertTrue(stats.hitCount() > 0);
        assertTrue(stats.loadSuccessCount() > 0);
        assertEquals(0, stats.loadFailureCount());
    }

    @Test
    void testNullKeyThrowsException() {
        Cache<String, String> cache = CacheBuilder.newBuilder().build();

        assertThrows(NullPointerException.class, () -> cache.put(null, "value"));
        assertThrows(NullPointerException.class, () -> cache.getIfPresent(null));
        assertThrows(NullPointerException.class, () -> cache.invalidate(null));
    }

    @Test
    void testNullValueThrowsException() {
        Cache<String, String> cache = CacheBuilder.newBuilder().build();

        assertThrows(NullPointerException.class, () -> cache.put("key", null));
    }

    @Test
    void testRefresh() throws Exception {
        AtomicInteger loadCount = new AtomicInteger(0);

        CacheLoader<String, String> loader = new CacheLoader<String, String>() {
            @Override
            public String load(String key) {
                return "value_" + loadCount.incrementAndGet();
            }
        };

        LoadingCache<String, String> cache = CacheBuilder.newBuilder().build(loader);

        String value1 = cache.get("key");
        assertEquals("value_1", value1);

        // Refresh the key
        cache.refresh("key");
        Thread.sleep(100); // Wait for async refresh

        String value2 = cache.get("key");
        // Value should be updated after refresh
        assertTrue(value2.startsWith("value_"));
    }

    @Test
    void testEvictionStats() {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(2)
                .recordStats()
                .build();

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three"); // Should trigger eviction

        CacheStats stats = cache.stats();
        assertTrue(stats.evictionCount() > 0);
    }

    @Test
    void testZeroMaximumSize() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(0)
                .build();

        cache.put("key", "value");
        // With size 0, entry should be evicted immediately
        assertEquals(0, cache.size());
    }

    @Test
    void testInitialCapacityAndConcurrencyLevel() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .initialCapacity(100)
                .concurrencyLevel(8)
                .build();

        // Should work normally
        cache.put("key", "value");
        assertEquals("value", cache.getIfPresent("key"));
    }

    @Test
    void testBuilderValidation() {
        assertThrows(IllegalArgumentException.class,
            () -> CacheBuilder.newBuilder().initialCapacity(-1));

        assertThrows(IllegalArgumentException.class,
            () -> CacheBuilder.newBuilder().concurrencyLevel(0));

        assertThrows(IllegalArgumentException.class,
            () -> CacheBuilder.newBuilder().maximumSize(-1));

        assertThrows(IllegalArgumentException.class,
            () -> CacheBuilder.newBuilder().expireAfterWrite(-1, TimeUnit.SECONDS));

        assertThrows(IllegalArgumentException.class,
            () -> CacheBuilder.newBuilder().expireAfterAccess(-1, TimeUnit.SECONDS));
    }
}
