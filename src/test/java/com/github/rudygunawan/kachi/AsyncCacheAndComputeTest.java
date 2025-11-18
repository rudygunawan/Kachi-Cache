package com.github.rudygunawan.kachi;

import com.github.rudygunawan.kachi.api.AsyncCache;
import com.github.rudygunawan.kachi.api.AsyncLoadingCache;
import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AsyncCache and compute operations.
 */
class AsyncCacheAndComputeTest {

    // ========== AsyncCache Tests ==========

    @Test
    void testAsyncCacheBasicOperations() throws Exception {
        AsyncCache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .buildAsync();

        // Test put and getIfPresent
        cache.put("key1", "value1").join();
        CompletableFuture<String> future = cache.getIfPresent("key1");
        assertEquals("value1", future.join());

        // Test get with mapping function
        CompletableFuture<String> computed = cache.get("key2", key ->
                CompletableFuture.completedFuture("computed-" + key)
        );
        assertEquals("computed-key2", computed.join());
        assertEquals("computed-key2", cache.getIfPresent("key2").join());
    }

    @Test
    void testAsyncLoadingCache() throws Exception {
        AsyncLoadingCache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .buildAsync((key, executor) ->
                        CompletableFuture.supplyAsync(() -> "loaded-" + key, executor)
                );

        // Test automatic loading
        CompletableFuture<String> future = cache.get("key1");
        assertEquals("loaded-key1", future.join());

        // Verify it's cached
        assertEquals("loaded-key1", cache.getIfPresent("key1").join());
    }

    @Test
    void testAsyncCacheWithAsyncOperation() throws Exception {
        AsyncCache<Integer, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .buildAsync();

        // Simulate async computation
        CompletableFuture<Integer> result = cache.get(5, key ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(10); // Simulate async work
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return key * key;
                })
        );

        assertEquals(25, result.join());
    }

    // ========== Compute Operations Tests ==========

    @Test
    void testCompute() {
        Cache<String, Integer> cache = CacheBuilder.newBuilder().build();

        // Compute new value
        Integer result = cache.compute("counter", (k, v) -> (v == null) ? 1 : v + 1);
        assertEquals(1, result);

        // Increment existing value
        result = cache.compute("counter", (k, v) -> (v == null) ? 1 : v + 1);
        assertEquals(2, result);

        // Remove by returning null
        result = cache.compute("counter", (k, v) -> null);
        assertNull(result);
        assertNull(cache.getIfPresent("counter"));
    }

    @Test
    void testComputeIfAbsent() {
        Cache<String, String> cache = CacheBuilder.newBuilder().build();

        // Compute if absent - should compute
        String result = cache.computeIfAbsent("key1", k -> "value1");
        assertEquals("value1", result);

        // Compute if absent - should return existing
        result = cache.computeIfAbsent("key1", k -> "value2");
        assertEquals("value1", result); // Original value retained
    }

    @Test
    void testComputeIfPresent() {
        Cache<String, String> cache = CacheBuilder.newBuilder().build();

        // Compute if present - key doesn't exist
        String result = cache.computeIfPresent("key1", (k, v) -> v + "-updated");
        assertNull(result);

        // Add value and update
        cache.put("key1", "value1");
        result = cache.computeIfPresent("key1", (k, v) -> v + "-updated");
        assertEquals("value1-updated", result);

        // Remove by returning null
        result = cache.computeIfPresent("key1", (k, v) -> null);
        assertNull(result);
        assertNull(cache.getIfPresent("key1"));
    }

    @Test
    void testMerge() {
        Cache<String, Integer> cache = CacheBuilder.newBuilder().build();

        // Merge into non-existent key
        Integer result = cache.merge("counter", 1, Integer::sum);
        assertEquals(1, result);

        // Merge into existing key
        result = cache.merge("counter", 5, Integer::sum);
        assertEquals(6, result);

        result = cache.merge("counter", 10, Integer::sum);
        assertEquals(16, result);
    }

    @Test
    void testAtomicCounterWithCompute() {
        Cache<String, AtomicInteger> cache = CacheBuilder.newBuilder().build();

        // Initialize counter
        cache.compute("counter", (k, v) -> v == null ? new AtomicInteger(0) : v);

        // Increment using computeIfPresent
        for (int i = 0; i < 100; i++) {
            cache.computeIfPresent("counter", (k, v) -> {
                v.incrementAndGet();
                return v;
            });
        }

        assertEquals(100, cache.getIfPresent("counter").get());
    }

    @Test
    void testComputeOperationsWithBothStrategies() {
        // Test with HIGH_PERFORMANCE strategy
        Cache<String, Integer> highPerfCache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .build();

        Integer result = highPerfCache.computeIfAbsent("key1", k -> 42);
        assertEquals(42, result);
        assertEquals(42, highPerfCache.merge("key1", 8, Integer::sum));

        // Test with PRECISION strategy
        Cache<String, Integer> precisionCache = CacheBuilder.newBuilder()
                .strategy(com.github.rudygunawan.kachi.api.CacheStrategy.PRECISION)
                .maximumSize(100)
                .build();

        result = precisionCache.computeIfAbsent("key1", k -> 42);
        assertEquals(42, result);
        assertEquals(42, precisionCache.merge("key1", 8, Integer::sum));
    }

    @Test
    void testSynchronousVsAsyncCache() throws Exception {
        // Sync cache
        Cache<String, String> syncCache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .build();

        syncCache.put("key1", "value1");
        assertEquals("value1", syncCache.getIfPresent("key1"));

        // Async cache wrapping the same cache
        AsyncCache<String, String> asyncCache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .buildAsync();

        asyncCache.put("key2", "value2").join();
        assertEquals("value2", asyncCache.getIfPresent("key2").join());

        // Access synchronous cache from async cache
        assertEquals("value2", asyncCache.synchronous().getIfPresent("key2"));
    }
}
