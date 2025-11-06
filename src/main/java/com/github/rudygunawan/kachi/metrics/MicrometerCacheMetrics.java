package com.github.rudygunawan.kachi.metrics;

import com.github.rudygunawan.kachi.api.Cache;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.Collections;

/**
 * Micrometer integration for Kachi Cache metrics.
 * Binds cache statistics to a MeterRegistry for monitoring and observability.
 *
 * <p>Exposes the following metrics:
 * <ul>
 *   <li>cache.size - Current number of entries
 *   <li>cache.hits - Total number of cache hits
 *   <li>cache.misses - Total number of cache misses
 *   <li>cache.evictions - Total number of evictions
 *   <li>cache.loads - Total number of loads (success + failure)
 *   <li>cache.load.duration - Total time spent loading
 *   <li>cache.hit.ratio - Cache hit rate (0.0 to 1.0)
 *   <li>cache.idle.entries - Number of idle entries (not accessed recently)
 *   <li>cache.memory.estimated - Estimated memory usage in bytes
 *   <li>cache.entry.size.average - Average size of each cache entry in bytes
 *   <li>cache.size.mb - Cache size in megabytes
 *   <li>cache.size.gb - Cache size in gigabytes
 *   <li>cache.expiry.less_than_1min - Entries expiring in < 1 minute
 *   <li>cache.expiry.less_than_5min - Entries expiring in < 5 minutes
 *   <li>cache.expiry.less_than_15min - Entries expiring in < 15 minutes
 *   <li>cache.expiry.less_than_1hour - Entries expiring in < 1 hour
 *   <li>cache.expiry.less_than_24hours - Entries expiring in < 24 hours
 *   <li>cache.expiry.more_than_24hours - Entries expiring in > 24 hours
 *   <li>cache.expiry.never - Entries that never expire
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * MeterRegistry registry = new SimpleMeterRegistry();
 * Cache<String, User> cache = CacheBuilder.newBuilder()
 *     .maximumSize(1000)
 *     .recordStats()
 *     .build();
 *
 * MicrometerCacheMetrics.monitor(registry, cache, "userCache");
 * }</pre>
 */
public class MicrometerCacheMetrics implements MeterBinder {

    private final CacheMetrics cache;
    private final String cacheName;
    private final Iterable<Tag> tags;

    /**
     * Creates a new MicrometerCacheMetrics instance.
     *
     * @param cache the cache to monitor
     * @param cacheName the name of the cache for metric tags
     * @param tags additional tags to apply to all metrics
     */
    public MicrometerCacheMetrics(CacheMetrics cache, String cacheName, Iterable<Tag> tags) {
        this.cache = cache;
        this.cacheName = cacheName;
        this.tags = tags;
    }

    /**
     * Convenience method to monitor a cache with Micrometer.
     *
     * @param registry the meter registry
     * @param cache the cache to monitor
     * @param cacheName the name of the cache
     * @param <C> the cache type
     * @return the cache (for chaining)
     */
    public static <C extends Cache<?, ?> & CacheMetrics> C monitor(
            MeterRegistry registry, C cache, String cacheName) {
        return monitor(registry, cache, cacheName, Collections.emptyList());
    }

    /**
     * Convenience method to monitor a cache with Micrometer with additional tags.
     *
     * @param registry the meter registry
     * @param cache the cache to monitor
     * @param cacheName the name of the cache
     * @param tags additional tags
     * @param <C> the cache type
     * @return the cache (for chaining)
     */
    public static <C extends Cache<?, ?> & CacheMetrics> C monitor(
            MeterRegistry registry, C cache, String cacheName, Iterable<Tag> tags) {
        new MicrometerCacheMetrics(cache, cacheName, tags).bindTo(registry);
        return cache;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Tags allTags = Tags.of("cache", cacheName).and(tags);

        // Cache size
        Gauge.builder("cache.size", cache, CacheMetrics::size)
                .tags(allTags)
                .description("Current number of entries in the cache")
                .register(registry);

        // Hit count
        FunctionCounter.builder("cache.hits", cache, CacheMetrics::hitCount)
                .tags(allTags)
                .description("Total number of cache hits")
                .register(registry);

        // Miss count
        FunctionCounter.builder("cache.misses", cache, CacheMetrics::missCount)
                .tags(allTags)
                .description("Total number of cache misses")
                .register(registry);

        // Eviction count
        FunctionCounter.builder("cache.evictions", cache, CacheMetrics::evictionCount)
                .tags(allTags)
                .description("Total number of cache evictions")
                .register(registry);

        // Load success count
        FunctionCounter.builder("cache.loads", cache, c -> c.loadSuccessCount() + c.loadFailureCount())
                .tags(allTags.and("result", "all"))
                .description("Total number of cache loads")
                .register(registry);

        FunctionCounter.builder("cache.loads", cache, CacheMetrics::loadSuccessCount)
                .tags(allTags.and("result", "success"))
                .description("Number of successful cache loads")
                .register(registry);

        FunctionCounter.builder("cache.loads", cache, CacheMetrics::loadFailureCount)
                .tags(allTags.and("result", "failure"))
                .description("Number of failed cache loads")
                .register(registry);

        // Load duration
        FunctionTimer.builder("cache.load.duration", cache,
                        c -> c.loadSuccessCount() + c.loadFailureCount(),
                        c -> c.totalLoadTimeNanos(),
                        java.util.concurrent.TimeUnit.NANOSECONDS)
                .tags(allTags)
                .description("Time spent loading cache values")
                .register(registry);

        // Hit ratio (derived metric)
        Gauge.builder("cache.hit.ratio", cache, c -> {
                    long hits = c.hitCount();
                    long misses = c.missCount();
                    long total = hits + misses;
                    return total == 0 ? 0.0 : (double) hits / total;
                })
                .tags(allTags)
                .description("Cache hit ratio (0.0 to 1.0)")
                .register(registry);

        // Idle entries
        Gauge.builder("cache.idle.entries", cache, CacheMetrics::idleEntryCount)
                .tags(allTags)
                .description("Number of idle entries (not accessed recently)")
                .register(registry);

        // Estimated memory usage
        Gauge.builder("cache.memory.estimated", cache, CacheMetrics::estimatedMemoryUsageBytes)
                .tags(allTags)
                .baseUnit("bytes")
                .description("Estimated memory usage of the cache")
                .register(registry);

        // Average entry size
        Gauge.builder("cache.entry.size.average", cache, CacheMetrics::averageEntrySizeBytes)
                .tags(allTags)
                .baseUnit("bytes")
                .description("Average size of each cache entry in bytes")
                .register(registry);

        // Cache size in MB
        Gauge.builder("cache.size.mb", cache, CacheMetrics::cacheSizeMB)
                .tags(allTags)
                .baseUnit("megabytes")
                .description("Cache size in megabytes")
                .register(registry);

        // Cache size in GB
        Gauge.builder("cache.size.gb", cache, CacheMetrics::cacheSizeGB)
                .tags(allTags)
                .baseUnit("gigabytes")
                .description("Cache size in gigabytes")
                .register(registry);

        // Expiry distribution - breakdown by time windows
        Gauge.builder("cache.expiry.less_than_1min", cache,
                        c -> c.expiryDistribution().getLessThan1Minute())
                .tags(allTags)
                .description("Number of entries expiring in less than 1 minute")
                .register(registry);

        Gauge.builder("cache.expiry.less_than_5min", cache,
                        c -> c.expiryDistribution().getLessThan5Minutes())
                .tags(allTags)
                .description("Number of entries expiring in less than 5 minutes")
                .register(registry);

        Gauge.builder("cache.expiry.less_than_15min", cache,
                        c -> c.expiryDistribution().getLessThan15Minutes())
                .tags(allTags)
                .description("Number of entries expiring in less than 15 minutes")
                .register(registry);

        Gauge.builder("cache.expiry.less_than_1hour", cache,
                        c -> c.expiryDistribution().getLessThan1Hour())
                .tags(allTags)
                .description("Number of entries expiring in less than 1 hour")
                .register(registry);

        Gauge.builder("cache.expiry.less_than_24hours", cache,
                        c -> c.expiryDistribution().getLessThan24Hours())
                .tags(allTags)
                .description("Number of entries expiring in less than 24 hours")
                .register(registry);

        Gauge.builder("cache.expiry.more_than_24hours", cache,
                        c -> c.expiryDistribution().getMoreThan24Hours())
                .tags(allTags)
                .description("Number of entries expiring in more than 24 hours")
                .register(registry);

        Gauge.builder("cache.expiry.never", cache,
                        c -> c.expiryDistribution().getNeverExpires())
                .tags(allTags)
                .description("Number of entries that never expire")
                .register(registry);
    }
}
