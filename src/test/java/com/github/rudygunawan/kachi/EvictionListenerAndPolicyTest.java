package com.github.rudygunawan.kachi;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.CacheStrategy;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.listener.EvictionListener;
import com.github.rudygunawan.kachi.policy.EvictionPolicy;
import com.github.rudygunawan.kachi.policy.Policy;
import com.github.rudygunawan.kachi.policy.RemovalCause;
import com.github.rudygunawan.kachi.policy.Strength;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EvictionListener, Policy introspection, and reference strength configuration.
 */
class EvictionListenerAndPolicyTest {

    // ========== EvictionListener Tests ==========

    @Test
    void testEvictionListenerOnlyCalledForEvictions() {
        List<RemovalCause> evictionCauses = new ArrayList<>();

        EvictionListener<String, String> listener = (key, value, cause) -> {
            evictionCauses.add(cause);
        };

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(2)
                .evictionListener(listener)
                .build();

        // INSERT - should NOT trigger eviction listener
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // SIZE eviction - SHOULD trigger eviction listener
        cache.put("key3", "value3");

        // EXPLICIT removal - should NOT trigger eviction listener
        cache.invalidate("key3");

        // Verify only SIZE eviction was recorded
        assertEquals(1, evictionCauses.size());
        assertEquals(RemovalCause.SIZE, evictionCauses.get(0));
    }

    @Test
    void testEvictionListenerOnExpiration() throws InterruptedException {
        List<RemovalCause> evictionCauses = new ArrayList<>();

        EvictionListener<String, String> listener = (key, value, cause) -> {
            evictionCauses.add(cause);
        };

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MILLISECONDS)
                .evictionListener(listener)
                .build();

        cache.put("key1", "value1");

        // Wait for expiration
        Thread.sleep(50);

        // Trigger cleanup to fire eviction listener
        cache.cleanUp();

        // Verify EXPIRED eviction was recorded
        assertTrue(evictionCauses.contains(RemovalCause.EXPIRED));
    }

    @Test
    void testEvictionListenerWithBothStrategies() {
        // Test with HIGH_PERFORMANCE strategy
        AtomicInteger highPerfEvictions = new AtomicInteger(0);
        Cache<String, String> highPerfCache = CacheBuilder.newBuilder()
                .maximumSize(2)
                .evictionListener((k, v, c) -> highPerfEvictions.incrementAndGet())
                .build();

        highPerfCache.put("key1", "value1");
        highPerfCache.put("key2", "value2");
        highPerfCache.put("key3", "value3"); // Trigger eviction

        assertTrue(highPerfEvictions.get() > 0, "High-performance cache should trigger eviction listener");

        // Test with PRECISION strategy
        AtomicInteger precisionEvictions = new AtomicInteger(0);
        Cache<String, String> precisionCache = CacheBuilder.newBuilder()
                .strategy(CacheStrategy.PRECISION)
                .maximumSize(2)
                .evictionListener((k, v, c) -> precisionEvictions.incrementAndGet())
                .build();

        precisionCache.put("key1", "value1");
        precisionCache.put("key2", "value2");
        precisionCache.put("key3", "value3"); // Trigger eviction

        assertEquals(1, precisionEvictions.get(), "Precision cache should trigger eviction listener");
    }

    @Test
    void testEvictionListenerExceptionHandling() {
        // Listener that throws exception
        EvictionListener<String, String> faultyListener = (k, v, c) -> {
            throw new RuntimeException("Listener error");
        };

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(1)
                .evictionListener(faultyListener)
                .build();

        // Should not throw exception even though listener throws
        assertDoesNotThrow(() -> {
            cache.put("key1", "value1");
            cache.put("key2", "value2"); // Trigger eviction with faulty listener
        });
    }

    // ========== Policy Introspection Tests ==========

    @Test
    void testPolicyEvictionBasic() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .build();

        Policy<String, String> policy = cache.policy();

        assertTrue(policy.eviction().isPresent());

        Policy.Eviction<String, String> eviction = policy.eviction().get();
        assertEquals(1000, eviction.getMaximum());
        assertEquals(0, eviction.weightedSize());
        assertFalse(eviction.isWeighted());
        assertEquals(EvictionPolicy.LRU, eviction.getEvictionPolicy());
    }

    @Test
    void testPolicyEvictionDynamicResize() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(5)
                .build();

        // Add entries
        for (int i = 0; i < 5; i++) {
            cache.put("key" + i, "value" + i);
        }

        Policy<String, String> policy = cache.policy();
        Policy.Eviction<String, String> eviction = policy.eviction().get();

        assertEquals(5, eviction.getMaximum());
        assertEquals(5, eviction.weightedSize());

        // Dynamically resize to smaller size
        eviction.setMaximum(3);
        assertEquals(3, eviction.getMaximum());

        // Trigger cleanup to enforce new size
        cache.cleanUp();

        // Size should be reduced after cleanup
        assertTrue(cache.size() <= 3, "Cache size should be <= 3 after resize and cleanup");
    }

    @Test
    void testPolicyExpirationBasic() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .build();

        Policy<String, String> policy = cache.policy();

        assertTrue(policy.expiration().isPresent());

        Policy.Expiration<String, String> expiration = policy.expiration().get();
        assertEquals(TimeUnit.MINUTES.toNanos(10), expiration.getExpiresAfterWrite());
        assertEquals(TimeUnit.MINUTES.toNanos(5), expiration.getExpiresAfterAccess());
    }

    @Test
    void testPolicyExpirationDynamicModification() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();

        Policy<String, String> policy = cache.policy();
        Policy.Expiration<String, String> expiration = policy.expiration().get();

        // Dynamically change TTL
        expiration.setExpiresAfterWrite(TimeUnit.MINUTES.toNanos(20));
        assertEquals(TimeUnit.MINUTES.toNanos(20), expiration.getExpiresAfterWrite());

        expiration.setExpiresAfterAccess(TimeUnit.MINUTES.toNanos(15));
        assertEquals(TimeUnit.MINUTES.toNanos(15), expiration.getExpiresAfterAccess());
    }

    @Test
    void testPolicyExpirationAgeOf() throws InterruptedException {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();

        cache.put("key1", "value1");

        // Wait a bit
        Thread.sleep(100);

        Policy<String, String> policy = cache.policy();
        Policy.Expiration<String, String> expiration = policy.expiration().get();

        long age = expiration.ageOf("key1");
        assertTrue(age > 0, "Entry age should be > 0");
        assertTrue(age >= TimeUnit.MILLISECONDS.toNanos(50), "Entry age should be at least 50ms");

        // Non-existent key should return -1
        assertEquals(-1, expiration.ageOf("nonexistent"));
    }

    @Test
    void testPolicyWithNoEviction() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .build(); // No maximumSize configured

        Policy<String, String> policy = cache.policy();

        assertFalse(policy.eviction().isPresent());
    }

    @Test
    void testPolicyWithNoExpiration() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .build(); // No expiration configured

        Policy<String, String> policy = cache.policy();

        assertFalse(policy.expiration().isPresent());
    }

    @Test
    void testPolicyWithBothStrategies() {
        // Test with HIGH_PERFORMANCE strategy
        Cache<String, String> highPerfCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .build();

        Policy<String, String> highPerfPolicy = highPerfCache.policy();
        assertTrue(highPerfPolicy.eviction().isPresent());
        assertEquals(1000, highPerfPolicy.eviction().get().getMaximum());

        // Test with PRECISION strategy
        Cache<String, String> precisionCache = CacheBuilder.newBuilder()
                .strategy(CacheStrategy.PRECISION)
                .maximumSize(500)
                .build();

        Policy<String, String> precisionPolicy = precisionCache.policy();
        assertTrue(precisionPolicy.eviction().isPresent());
        assertEquals(500, precisionPolicy.eviction().get().getMaximum());
    }

    // ========== Reference Strength Configuration Tests ==========

    @Test
    void testWeakKeysConfiguration() {
        var builder = CacheBuilder.newBuilder()
                .weakKeys();

        assertEquals(Strength.WEAK, builder.getKeyStrength());
        assertEquals(Strength.STRONG, builder.getValueStrength()); // Default
    }

    @Test
    void testWeakValuesConfiguration() {
        var builder = CacheBuilder.newBuilder()
                .weakValues();

        assertEquals(Strength.STRONG, builder.getKeyStrength()); // Default
        assertEquals(Strength.WEAK, builder.getValueStrength());
    }

    @Test
    void testSoftValuesConfiguration() {
        var builder = CacheBuilder.newBuilder()
                .softValues();

        assertEquals(Strength.STRONG, builder.getKeyStrength()); // Default
        assertEquals(Strength.SOFT, builder.getValueStrength());
    }

    @Test
    void testWeakKeysSoftValuesConfiguration() {
        var builder = CacheBuilder.newBuilder()
                .weakKeys()
                .softValues();

        assertEquals(Strength.WEAK, builder.getKeyStrength());
        assertEquals(Strength.SOFT, builder.getValueStrength());
    }

    @Test
    void testReferenceStrengthDefaults() {
        var builder = CacheBuilder.newBuilder();

        assertEquals(Strength.STRONG, builder.getKeyStrength());
        assertEquals(Strength.STRONG, builder.getValueStrength());
    }

    @Test
    void testSoftValuesOverridesWeakValues() {
        var builder = CacheBuilder.newBuilder()
                .weakValues()
                .softValues(); // Should override weak

        assertEquals(Strength.SOFT, builder.getValueStrength());
    }

    // ========== Integration Tests ==========

    @Test
    void testEvictionListenerAndPolicyTogether() {
        AtomicInteger evictionCount = new AtomicInteger(0);

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(5)
                .evictionListener((k, v, c) -> evictionCount.incrementAndGet())
                .build();

        // Add entries to trigger evictions
        for (int i = 0; i < 10; i++) {
            cache.put("key" + i, "value" + i);
        }

        // Verify evictions occurred
        assertTrue(evictionCount.get() > 0);

        // Use policy to resize
        Policy<String, String> policy = cache.policy();
        policy.eviction().ifPresent(eviction -> {
            eviction.setMaximum(3);
        });

        cache.cleanUp();

        // Verify more evictions after resize
        int evictionsAfterResize = evictionCount.get();
        assertTrue(evictionsAfterResize > 0);
    }

    @Test
    void testPolicyEvictionWithDifferentPolicies() {
        // Test with LFU policy
        Cache<String, String> lfuCache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .evictionPolicy(EvictionPolicy.LFU)
                .build();

        assertEquals(EvictionPolicy.LFU, lfuCache.policy().eviction().get().getEvictionPolicy());

        // Test with FIFO policy
        Cache<String, String> fifoCache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .evictionPolicy(EvictionPolicy.FIFO)
                .build();

        assertEquals(EvictionPolicy.FIFO, fifoCache.policy().eviction().get().getEvictionPolicy());

        // Test with WINDOW_TINY_LFU policy
        Cache<String, String> tinyLfuCache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .evictionPolicy(EvictionPolicy.WINDOW_TINY_LFU)
                .build();

        assertEquals(EvictionPolicy.WINDOW_TINY_LFU, tinyLfuCache.policy().eviction().get().getEvictionPolicy());
    }
}
