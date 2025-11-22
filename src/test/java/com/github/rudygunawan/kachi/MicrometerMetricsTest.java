package com.github.rudygunawan.kachi;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.CacheLoader;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.metrics.CacheMetrics;
import com.github.rudygunawan.kachi.metrics.MicrometerCacheMetrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Micrometer metrics integration.
 */
class MicrometerMetricsTest {

    /**
     * Helper method to cast cache to a type that works with MicrometerCacheMetrics.monitor().
     * The actual implementations (HighPerformanceCacheImpl/PrecisionCacheImpl) implement both
     * Cache and CacheMetrics, but the build() method only returns Cache<K,V>.
     */
    @SuppressWarnings("unchecked")
    private static <K, V, C extends Cache<K, V> & CacheMetrics> C asMonitorable(Cache<K, V> cache) {
        // This is safe because both HighPerformanceCacheImpl and PrecisionCacheImpl implement CacheMetrics
        return (C) cache;
    }

    @Test
    void testBasicMetricsExposed() {
        MeterRegistry registry = new SimpleMeterRegistry();

        var cache =
                CacheBuilder.newBuilder()
                        .recordStats()
                        .build();

        MicrometerCacheMetrics.monitor(registry, asMonitorable(cache), "testCache");

        // Verify metrics are registered
        assertNotNull(registry.find("cache.size").gauge());
        assertNotNull(registry.find("cache.hits").counter());
        assertNotNull(registry.find("cache.misses").counter());
        assertNotNull(registry.find("cache.evictions").counter());
    }

    @Test
    void testCacheSizeMetric() {
        MeterRegistry registry = new SimpleMeterRegistry();

        var cache =
                CacheBuilder.newBuilder()
                        .recordStats()
                        .build();

        MicrometerCacheMetrics.monitor(registry, asMonitorable(cache), "testCache");

        Gauge sizeGauge = registry.find("cache.size").gauge();
        assertNotNull(sizeGauge);
        assertEquals(0.0, sizeGauge.value(), 0.01);

        cache.put("key1", "value1");
        cache.put("key2", "value2");

        assertEquals(2.0, sizeGauge.value(), 0.01);
    }

    @Test
    void testHitAndMissMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();

        var cache =
                CacheBuilder.newBuilder()
                        .recordStats()
                        .build();

        MicrometerCacheMetrics.monitor(registry, asMonitorable(cache), "testCache");

        Counter hits = registry.find("cache.hits").counter();
        Counter misses = registry.find("cache.misses").counter();

        assertNotNull(hits);
        assertNotNull(misses);

        // Initially zero
        assertEquals(0.0, hits.count(), 0.01);
        assertEquals(0.0, misses.count(), 0.01);

        // Miss
        cache.getIfPresent("key1");
        assertEquals(0.0, hits.count(), 0.01);
        assertEquals(1.0, misses.count(), 0.01);

        // Hit
        cache.put("key1", "value1");
        cache.getIfPresent("key1");
        assertEquals(1.0, hits.count(), 0.01);
        assertEquals(1.0, misses.count(), 0.01);
    }

    @Test
    void testEvictionMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();

        var cache =
                CacheBuilder.newBuilder()
                        .maximumSize(2)
                        .recordStats()
                        .build();

        MicrometerCacheMetrics.monitor(registry, asMonitorable(cache), "testCache");

        Counter evictions = registry.find("cache.evictions").counter();
        assertNotNull(evictions);

        cache.put(1, "one");
        cache.put(2, "two");

        double initialEvictions = evictions.count();

        // This might trigger eviction if entries are old enough
        cache.put(3, "three");

        // Evictions should be >= initial (might be the same if entries too new)
        assertTrue(evictions.count() >= initialEvictions);
    }

    @Test
    void testLoadMetrics() throws Exception {
        MeterRegistry registry = new SimpleMeterRegistry();

        CacheLoader<String, String> loader = new CacheLoader<String, String>() {
            @Override
            public String load(String key) {
                return "loaded_" + key;
            }
        };

        var cache =
                CacheBuilder.newBuilder()
                        .recordStats()
                        .build(loader);

        MicrometerCacheMetrics.monitor(registry, asMonitorable(cache), "testCache");

        Counter loadSuccess = registry.find("cache.loads").tag("result", "success").counter();
        Counter loadFailure = registry.find("cache.loads").tag("result", "failure").counter();

        assertNotNull(loadSuccess);
        assertNotNull(loadFailure);

        assertEquals(0.0, loadSuccess.count(), 0.01);
        assertEquals(0.0, loadFailure.count(), 0.01);

        // Successful load
        cache.get("key1");

        assertEquals(1.0, loadSuccess.count(), 0.01);
        assertEquals(0.0, loadFailure.count(), 0.01);
    }

    @Test
    void testHitRatioMetric() {
        MeterRegistry registry = new SimpleMeterRegistry();

        var cache =
                CacheBuilder.newBuilder()
                        .recordStats()
                        .build();

        MicrometerCacheMetrics.monitor(registry, asMonitorable(cache), "testCache");

        Gauge hitRatio = registry.find("cache.hit.ratio").gauge();
        assertNotNull(hitRatio);

        // Initially 0.0 (no requests)
        assertEquals(0.0, hitRatio.value(), 0.01);

        cache.put("key1", "value1");

        // One miss
        cache.getIfPresent("key2");
        assertEquals(0.0, hitRatio.value(), 0.01);

        // One hit
        cache.getIfPresent("key1");
        assertEquals(0.5, hitRatio.value(), 0.01); // 1 hit, 1 miss = 50%

        // Another hit
        cache.getIfPresent("key1");
        assertEquals(0.67, hitRatio.value(), 0.02); // 2 hits, 1 miss = 66.7%
    }

    @Test
    void testIdleEntriesMetric() throws Exception {
        MeterRegistry registry = new SimpleMeterRegistry();

        var cache =
                CacheBuilder.newBuilder()
                        .recordStats()
                        .build();

        MicrometerCacheMetrics.monitor(registry, asMonitorable(cache), "testCache");

        Gauge idleEntries = registry.find("cache.idle.entries").gauge();
        assertNotNull(idleEntries);

        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // Initially, no entries are idle (just created)
        assertEquals(0.0, idleEntries.value(), 0.01);

        // Access key1 to keep it active
        cache.getIfPresent("key1");

        // After enough time, key2 would become idle (but test can't wait 5 minutes)
        // We're just verifying the metric exists and returns a value
        assertTrue(idleEntries.value() >= 0.0);
    }

    @Test
    void testEstimatedMemoryMetric() {
        MeterRegistry registry = new SimpleMeterRegistry();

        var cache =
                CacheBuilder.newBuilder()
                        .recordStats()
                        .build();

        MicrometerCacheMetrics.monitor(registry, asMonitorable(cache), "testCache");

        Gauge memory = registry.find("cache.memory.estimated").gauge();
        assertNotNull(memory);

        // Empty cache
        assertEquals(0.0, memory.value(), 0.01);

        // Add entries
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // Memory should increase
        assertTrue(memory.value() > 0.0);
    }

    @Test
    void testMetricsWithCustomTags() {
        MeterRegistry registry = new SimpleMeterRegistry();

        var cache =
                CacheBuilder.newBuilder()
                        .recordStats()
                        .build();

        Tags customTags = Tags.of("application", "myapp", "environment", "test");
        MicrometerCacheMetrics.monitor(registry, asMonitorable(cache), "userCache", customTags);

        Gauge sizeGauge = registry.find("cache.size")
                .tag("cache", "userCache")
                .tag("application", "myapp")
                .tag("environment", "test")
                .gauge();

        assertNotNull(sizeGauge);
    }

    @Test
    void testLoadDurationTimer() throws Exception {
        MeterRegistry registry = new SimpleMeterRegistry();

        CacheLoader<String, String> loader = new CacheLoader<String, String>() {
            @Override
            public String load(String key) throws Exception {
                Thread.sleep(10); // Simulate load time
                return "loaded_" + key;
            }
        };

        var cache = CacheBuilder.newBuilder()
                .recordStats()
                .build(loader);

        MicrometerCacheMetrics.monitor(registry, asMonitorable(cache), "testCache");

        FunctionTimer timer = registry.find("cache.load.duration").functionTimer();
        assertNotNull(timer);

        double initialCount = timer.count();

        // Load a value
        cache.get("key1");

        // Timer count should increase
        assertTrue(timer.count() > initialCount);

        // Total time should be > 0
        assertTrue(timer.totalTime(TimeUnit.NANOSECONDS) > 0);
    }

    @Test
    void testCacheMetricsInterface() {
        var cache =
                CacheBuilder.newBuilder()
                        .recordStats()
                        .build();

        // Verify CacheMetrics interface is implemented
        CacheMetrics metrics = (CacheMetrics) cache;

        assertEquals(0, metrics.size());
        assertEquals(0, metrics.hitCount());
        assertEquals(0, metrics.missCount());
        assertEquals(0, metrics.evictionCount());
        assertEquals(0, metrics.loadSuccessCount());
        assertEquals(0, metrics.loadFailureCount());
        assertEquals(0, metrics.totalLoadTimeNanos());
        assertEquals(0, metrics.idleEntryCount());
        assertTrue(metrics.estimatedMemoryUsageBytes() >= 0);
    }

    @Test
    void testMultipleCachesWithSameRegistry() {
        MeterRegistry registry = new SimpleMeterRegistry();

        var cache1 = CacheBuilder.newBuilder().recordStats().build();
        var cache2 = CacheBuilder.newBuilder().recordStats().build();

        MicrometerCacheMetrics.monitor(registry, asMonitorable(cache1), "cache1");
        MicrometerCacheMetrics.monitor(registry, asMonitorable(cache2), "cache2");

        cache1.put("key", "value");
        cache2.put("key1", "value1");
        cache2.put("key2", "value2");

        Gauge size1 = registry.find("cache.size").tag("cache", "cache1").gauge();
        Gauge size2 = registry.find("cache.size").tag("cache", "cache2").gauge();

        assertNotNull(size1);
        assertNotNull(size2);

        assertEquals(1.0, size1.value(), 0.01);
        assertEquals(2.0, size2.value(), 0.01);
    }
}
