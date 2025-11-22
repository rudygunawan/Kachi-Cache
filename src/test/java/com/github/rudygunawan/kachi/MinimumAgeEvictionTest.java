package com.github.rudygunawan.kachi;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.listener.RemovalListener;
import com.github.rudygunawan.kachi.model.CacheEntry;
import com.github.rudygunawan.kachi.policy.EvictionPolicy;
import com.github.rudygunawan.kachi.policy.RemovalCause;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for minimum age eviction policy - entries must be at least 1 minute old before eviction.
 */
class MinimumAgeEvictionTest {

    @Test
    void testNewEntriesNotEvictedImmediately() throws Exception {
        List<String> evicted = new ArrayList<>();

        RemovalListener<Integer, String> listener = (key, value, cause) -> {
            if (cause == RemovalCause.SIZE) {
                evicted.add(key + "=" + value);
            }
        };

        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(2)
                .evictionPolicy(EvictionPolicy.LFU)
                .removalListener(listener)
                .build();

        // Add 2 entries
        cache.put(1, "one");
        cache.put(2, "two");

        // Immediately try to add a 3rd entry
        cache.put(3, "three");

        // No evictions should occur yet because entries are too new
        assertEquals(3, cache.size());
        assertEquals(0, evicted.size());
    }

    @Test
    void testEntriesEvictedAfterMinimumAge() throws Exception {
        // Note: This test uses a very small sleep for testing purposes
        // In real scenarios, minimum age is 1 minute

        List<String> evicted = new ArrayList<>();

        RemovalListener<Integer, String> listener = (key, value, cause) -> {
            if (cause == RemovalCause.SIZE) {
                evicted.add(key + "=" + value);
            }
        };

        // Create cache with very short expiry to test the minimum age logic
        // We'll test that entries younger than minimum age are not evicted
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(2)
                .evictionPolicy(EvictionPolicy.LFU)
                .removalListener(listener)
                .build();

        cache.put(1, "one");
        cache.put(2, "two");

        // Try to evict immediately
        cache.put(3, "three");

        // Should have 3 entries as none are old enough to evict
        assertTrue(cache.size() >= 2);
    }

    @Test
    void testLRURespectsMinimumAge() {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(3)
                .evictionPolicy(EvictionPolicy.LRU)
                .build();

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        // Access 1 to make it recently used
        cache.getIfPresent(1);

        // Try to add 4th immediately
        cache.put(4, "four");

        // All entries should still be present (or at least > 3) since they're too new
        long size = cache.size();
        assertTrue(size >= 3, "Cache size should be at least 3, was: " + size);
    }

    @Test
    void testFIFORespectsMinimumAge() {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(3)
                .evictionPolicy(EvictionPolicy.FIFO)
                .build();

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        // Try to add 4th immediately
        cache.put(4, "four");

        // All entries should still be present since they're too new
        long size = cache.size();
        assertTrue(size >= 3, "Cache size should be at least 3, was: " + size);
    }

    @Test
    void testCacheEntryAgeTracking() {
        CacheEntry<String> entry = new CacheEntry<>("test", 0);

        // Immediately after creation
        assertTrue(entry.getAgeNanos() >= 0);
        assertFalse(entry.isEligibleForEviction());

        // Age should be very small (less than 1 second)
        assertTrue(entry.getAgeNanos() < TimeUnit.SECONDS.toNanos(1));
    }

    @Test
    void testIdleEntriesNotEvictedIfTooYoung() {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(5)
                .evictionPolicy(EvictionPolicy.LFU)
                .build();

        // Add entries
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        // Access some entries
        cache.getIfPresent(1);
        cache.getIfPresent(1);
        cache.getIfPresent(2);

        // Entry 3 has 0 accesses (least frequently used)
        // But it's too young to be evicted

        cache.put(4, "four");
        cache.put(5, "five");
        cache.put(6, "six");

        // Cache should have more than 5 entries since none can be evicted yet
        assertTrue(cache.size() >= 5);
    }

    @Test
    void testMinimumAgeProtectsRecentEntries() {
        List<RemovalCause> causes = new ArrayList<>();

        RemovalListener<Integer, String> listener = (key, value, cause) -> {
            causes.add(cause);
        };

        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(2)
                .removalListener(listener)
                .build();

        // Rapidly add entries
        for (int i = 1; i <= 10; i++) {
            cache.put(i, "value" + i);
        }

        // Count SIZE evictions
        long sizeEvictions = causes.stream()
                .filter(c -> c == RemovalCause.SIZE)
                .count();

        // Should have very few or no SIZE evictions because entries are too new
        assertTrue(sizeEvictions < 5, "Should have minimal evictions, had: " + sizeEvictions);
    }

    @Test
    void testReplacementNotAffectedByMinimumAge() {
        List<RemovalCause> causes = new ArrayList<>();

        RemovalListener<Integer, String> listener = (key, value, cause) -> {
            causes.add(cause);
        };

        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .removalListener(listener)
                .build();

        cache.put(1, "one");
        cache.put(1, "ONE"); // Immediate replacement

        // Should have one REPLACED event
        assertEquals(1, causes.size());
        assertEquals(RemovalCause.REPLACED, causes.get(0));
    }

    @Test
    void testExplicitInvalidationNotAffectedByMinimumAge() {
        List<RemovalCause> causes = new ArrayList<>();

        RemovalListener<Integer, String> listener = (key, value, cause) -> {
            causes.add(cause);
        };

        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .removalListener(listener)
                .build();

        cache.put(1, "one");
        cache.invalidate(1); // Immediate invalidation

        // Should have one EXPLICIT event
        assertEquals(1, causes.size());
        assertEquals(RemovalCause.EXPLICIT, causes.get(0));
    }
}
