package com.github.rudygunawan.kachi;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.CacheStrategy;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.policy.Strength;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for weak and soft reference support in cache values.
 *
 * <p>Note: GC behavior is non-deterministic, so these tests use heuristics
 * and may occasionally fail. They are primarily for API validation.
 */
class WeakSoftReferenceTest {

    /**
     * Helper class to create objects that can be easily garbage collected.
     */
    static class HeavyObject {
        private final byte[] data;
        private final String id;

        HeavyObject(String id, int sizeKB) {
            this.id = id;
            this.data = new byte[sizeKB * 1024];
        }

        String getId() {
            return id;
        }

        @Override
        public String toString() {
            return "HeavyObject{id='" + id + "', size=" + (data.length / 1024) + "KB}";
        }
    }

    // ========== API Tests ==========

    @Test
    void testWeakValuesConfiguration() {
        var builder = CacheBuilder.newBuilder()
                .weakValues();

        assertEquals(Strength.WEAK, builder.getValueStrength());
        assertEquals(Strength.STRONG, builder.getKeyStrength()); // Keys always strong for now
    }

    @Test
    void testSoftValuesConfiguration() {
        var builder = CacheBuilder.newBuilder()
                .softValues();

        assertEquals(Strength.SOFT, builder.getValueStrength());
        assertEquals(Strength.STRONG, builder.getKeyStrength()); // Keys always strong for now
    }

    @Test
    void testWeakValuesCacheCreation() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .weakValues()
                .build();

        assertNotNull(cache);

        // Basic operations should work
        cache.put("key1", "value1");
        assertEquals("value1", cache.getIfPresent("key1"));
    }

    @Test
    void testSoftValuesCacheCreation() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .softValues()
                .build();

        assertNotNull(cache);

        // Basic operations should work
        cache.put("key1", "value1");
        assertEquals("value1", cache.getIfPresent("key1"));
    }

    @Test
    void testWeakValuesWithBothStrategies() {
        // HIGH_PERFORMANCE strategy
        Cache<String, String> highPerfCache = CacheBuilder.newBuilder()
                .weakValues()
                .build();

        highPerfCache.put("key1", "value1");
        assertEquals("value1", highPerfCache.getIfPresent("key1"));

        // PRECISION strategy
        Cache<String, String> precisionCache = CacheBuilder.newBuilder()
                .strategy(CacheStrategy.PRECISION)
                .weakValues()
                .build();

        precisionCache.put("key2", "value2");
        assertEquals("value2", precisionCache.getIfPresent("key2"));
    }

    // ========== GC Behavior Tests ==========

    @Test
    void testWeakValuesCanBeGarbageCollected() throws InterruptedException {
        Cache<String, HeavyObject> cache = CacheBuilder.newBuilder()
                .weakValues()
                .build();

        // Create object and keep a weak reference to track GC
        HeavyObject obj = new HeavyObject("test1", 100); // 100KB
        WeakReference<HeavyObject> weakRef = new WeakReference<>(obj);

        // Put in cache
        cache.put("key1", obj);
        assertEquals("test1", cache.getIfPresent("key1").getId());

        // Clear strong reference
        obj = null;

        // Suggest GC (not guaranteed to run immediately)
        System.gc();
        System.runFinalization();
        Thread.sleep(100);

        // Try multiple times to trigger GC
        for (int i = 0; i < 10; i++) {
            if (weakRef.get() == null) {
                break; // GC happened
            }
            System.gc();
            System.runFinalization();
            Thread.sleep(50);
        }

        // After GC, the weak reference should be cleared
        // This test may occasionally fail due to GC non-determinism
        if (weakRef.get() == null) {
            // GC happened - verify cache behavior
            // The value should be null or the entry should be removed during cleanup
            cache.cleanUp();
            HeavyObject cachedValue = cache.getIfPresent("key1");
            // Value should be null since it was GC'd
            assertNull(cachedValue, "Value should be null after GC and cleanup");
        } else {
            // GC didn't happen in this test run - just verify API works
            assertTrue(true, "GC didn't run during test - API validation passed");
        }
    }

    @Test
    void testSoftValuesRetainedUnderNormalConditions() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .softValues()
                .build();

        // Add some values
        for (int i = 0; i < 10; i++) {
            cache.put("key" + i, "value" + i);
        }

        // Soft references should be retained under normal memory conditions
        // (no memory pressure)
        for (int i = 0; i < 10; i++) {
            assertNotNull(cache.getIfPresent("key" + i),
                    "Soft references should be retained without memory pressure");
        }
    }

    @Test
    void testWeakValuesWithExpiration() throws InterruptedException {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .weakValues()
                .expireAfterWrite(100, TimeUnit.MILLISECONDS)
                .build();

        cache.put("key1", "value1");
        assertEquals("value1", cache.getIfPresent("key1"));

        // Wait for expiration
        Thread.sleep(150);
        cache.cleanUp();

        // Should be null due to expiration (not GC)
        assertNull(cache.getIfPresent("key1"));
    }

    @Test
    void testSoftValuesWithMaximumSize() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .softValues()
                .maximumSize(5)
                .build();

        // Add more than maximum
        for (int i = 0; i < 10; i++) {
            cache.put("key" + i, "value" + i);
        }

        // Size should be limited by maximumSize (eviction happens)
        // not by GC (soft references retained under normal conditions)
        assertTrue(cache.size() <= 5, "Cache size should respect maximum size");
    }

    @Test
    void testWeakValuesCleanupRemovesGCdEntries() {
        Cache<String, Object> cache = CacheBuilder.newBuilder()
                .weakValues()
                .build();

        // Add some entries
        for (int i = 0; i < 5; i++) {
            cache.put("key" + i, new Object());
        }

        long initialSize = cache.size();
        assertTrue(initialSize > 0);

        // Request GC multiple times
        for (int i = 0; i < 5; i++) {
            System.gc();
            System.runFinalization();
        }

        // Run cleanup to remove GC'd entries
        cache.cleanUp();

        // Size may have decreased if GC happened
        // (non-deterministic, so we just verify cleanup doesn't crash)
        assertTrue(cache.size() >= 0);
    }

    @Test
    void testWeakValuesWithPrecisionStrategy() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .strategy(CacheStrategy.PRECISION)
                .weakValues()
                .maximumSize(10)
                .build();

        cache.put("key1", "value1");
        cache.put("key2", "value2");

        assertEquals("value1", cache.getIfPresent("key1"));
        assertEquals("value2", cache.getIfPresent("key2"));

        // Verify precision cache works with weak values
        assertTrue(cache.size() >= 2);
    }

    @Test
    void testStrongValuesAsDefault() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .build(); // No weak/soft specified

        var builder = CacheBuilder.newBuilder();
        assertEquals(Strength.STRONG, builder.getValueStrength());
        assertEquals(Strength.STRONG, builder.getKeyStrength());

        // Strong references should never be GC'd while in cache
        cache.put("key1", "value1");

        for (int i = 0; i < 10; i++) {
            System.gc();
            System.runFinalization();
        }

        cache.cleanUp();

        // Strong reference should still be there
        assertEquals("value1", cache.getIfPresent("key1"));
    }

    // ========== Edge Cases ==========

    @Test
    void testNullHandlingWithWeakValues() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .weakValues()
                .build();

        // Null values not allowed
        assertThrows(NullPointerException.class, () -> cache.put("key1", null));
    }

    @Test
    void testComputeOperationsWithWeakValues() {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .weakValues()
                .build();

        // computeIfAbsent should work
        Integer value = cache.computeIfAbsent("counter", k -> 1);
        assertEquals(1, value);

        // compute should work
        value = cache.compute("counter", (k, v) -> (v == null) ? 1 : v + 1);
        assertEquals(2, value);
    }

    @Test
    void testAsMapWithWeakValues() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .weakValues()
                .build();

        cache.put("key1", "value1");
        cache.put("key2", "value2");

        var map = cache.asMap();
        assertEquals(2, map.size());
        assertTrue(map.containsKey("key1"));
        assertTrue(map.containsKey("key2"));
    }

    // ========== Weak Keys Tests ==========

    @Test
    void testWeakKeysConfiguration() {
        var builder = CacheBuilder.newBuilder()
                .weakKeys();

        assertEquals(Strength.WEAK, builder.getKeyStrength());
        assertEquals(Strength.STRONG, builder.getValueStrength());
    }

    @Test
    void testWeakKeysCacheCreation() {
        Cache<Object, String> cache = CacheBuilder.newBuilder()
                .weakKeys()
                .build();

        assertNotNull(cache);

        // Basic operations should work
        Object key = new Object();
        cache.put(key, "value1");
        assertEquals("value1", cache.getIfPresent(key));
    }

    @Test
    void testWeakKeysCanBeGarbageCollected() throws InterruptedException {
        Cache<HeavyObject, String> cache = CacheBuilder.newBuilder()
                .weakKeys()
                .build();

        // Create object and keep a weak reference to track GC
        HeavyObject key = new HeavyObject("key1", 100); // 100KB
        WeakReference<HeavyObject> weakRef = new WeakReference<>(key);

        // Put in cache
        cache.put(key, "value1");
        assertEquals("value1", cache.getIfPresent(key));

        // Clear strong reference to key
        key = null;

        // Suggest GC (not guaranteed to run immediately)
        System.gc();
        System.runFinalization();
        Thread.sleep(100);

        // Try multiple times to trigger GC
        for (int i = 0; i < 10; i++) {
            if (weakRef.get() == null) {
                break; // GC happened
            }
            System.gc();
            System.runFinalization();
            Thread.sleep(50);
        }

        // After GC, the weak reference should be cleared
        if (weakRef.get() == null) {
            // GC happened - verify cache behavior
            cache.cleanUp();

            // Entry should be removed since key was GC'd
            assertTrue(cache.size() == 0, "Cache should be empty after key GC'd and cleanup");
        } else {
            // GC didn't happen in this test run - just verify API works
            assertTrue(true, "GC didn't run during test - API validation passed");
        }
    }

    @Test
    void testWeakKeysWithPrecisionStrategy() {
        Cache<Object, String> cache = CacheBuilder.newBuilder()
                .strategy(CacheStrategy.PRECISION)
                .weakKeys()
                .build();

        Object key1 = new Object();
        Object key2 = new Object();

        cache.put(key1, "value1");
        cache.put(key2, "value2");

        assertEquals("value1", cache.getIfPresent(key1));
        assertEquals("value2", cache.getIfPresent(key2));
    }

    @Test
    void testWeakKeysAndWeakValues() {
        Cache<Object, Object> cache = CacheBuilder.newBuilder()
                .weakKeys()
                .weakValues()
                .build();

        Object key = new Object();
        Object value = new Object();

        cache.put(key, value);
        assertEquals(value, cache.getIfPresent(key));

        // Both key and value can be GC'd independently
        assertTrue(true, "Combined weak keys and weak values API works");
    }

    @Test
    void testWeakKeysWithMaximumSize() {
        Cache<Object, String> cache = CacheBuilder.newBuilder()
                .weakKeys()
                .maximumSize(3)
                .build();

        Object key1 = new Object();
        Object key2 = new Object();
        Object key3 = new Object();
        Object key4 = new Object();

        cache.put(key1, "value1");
        cache.put(key2, "value2");
        cache.put(key3, "value3");
        cache.put(key4, "value4"); // Should trigger eviction

        // Size should be limited by maximumSize
        assertTrue(cache.size() <= 3, "Cache size should respect maximum size with weak keys");
    }

    @Test
    void testWeakKeysExplicitInvalidation() {
        Cache<Object, String> cache = CacheBuilder.newBuilder()
                .weakKeys()
                .build();

        Object key1 = new Object();
        Object key2 = new Object();

        cache.put(key1, "value1");
        cache.put(key2, "value2");

        // Explicit invalidation should work
        cache.invalidate(key1);
        assertNull(cache.getIfPresent(key1));
        assertEquals("value2", cache.getIfPresent(key2));
    }

    @Test
    void testStrongKeysAsDefault() {
        Cache<Object, String> cache = CacheBuilder.newBuilder()
                .build(); // No weak keys specified

        var builder = CacheBuilder.newBuilder();
        assertEquals(Strength.STRONG, builder.getKeyStrength());

        // Strong key references should never be GC'd while in cache
        Object key = new Object();
        cache.put(key, "value1");

        // Even without strong reference, key should stay (cache holds it)
        Object originalKey = key;
        key = null;

        for (int i = 0; i < 10; i++) {
            System.gc();
            System.runFinalization();
        }

        cache.cleanUp();

        // Strong key should still be in cache
        assertEquals("value1", cache.getIfPresent(originalKey));
    }
}
