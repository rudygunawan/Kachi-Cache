package com.github.rudygunawan.kachi.metrics;

/**
 * Interface for cache implementations to provide metrics data.
 * This is used by MicrometerCacheMetrics to collect and expose metrics.
 */
public interface CacheMetrics {

    /**
     * Returns the current number of entries in the cache.
     */
    long size();

    /**
     * Returns the total number of cache hits.
     */
    long hitCount();

    /**
     * Returns the total number of cache misses.
     */
    long missCount();

    /**
     * Returns the total number of evictions.
     */
    long evictionCount();

    /**
     * Returns the total number of successful loads.
     */
    long loadSuccessCount();

    /**
     * Returns the total number of failed loads.
     */
    long loadFailureCount();

    /**
     * Returns the total time spent loading in nanoseconds.
     */
    long totalLoadTimeNanos();

    /**
     * Returns the number of entries that have not been accessed recently (idle entries).
     * An entry is considered idle if it hasn't been accessed in the last 5 minutes.
     */
    long idleEntryCount();

    /**
     * Returns the estimated memory usage in bytes.
     */
    long estimatedMemoryUsageBytes();

    /**
     * Returns the average entry size in bytes.
     * This is calculated as total estimated memory / number of entries.
     */
    long averageEntrySizeBytes();

    /**
     * Returns the cache size in megabytes.
     * This is a convenience method that converts estimatedMemoryUsageBytes to MB.
     */
    default double cacheSizeMB() {
        return estimatedMemoryUsageBytes() / (1024.0 * 1024.0);
    }

    /**
     * Returns the cache size in gigabytes.
     * This is a convenience method that converts estimatedMemoryUsageBytes to GB.
     */
    default double cacheSizeGB() {
        return estimatedMemoryUsageBytes() / (1024.0 * 1024.0 * 1024.0);
    }

    /**
     * Returns expiry distribution statistics.
     * Provides a breakdown of entries by time until expiry.
     */
    ExpiryDistribution expiryDistribution();
}
