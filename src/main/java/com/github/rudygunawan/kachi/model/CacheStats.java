package com.github.rudygunawan.kachi.model;

import java.util.Objects;

/**
 * Statistics about the performance of a {@link Cache}. Instances of this class are immutable.
 *
 * <p>Cache statistics are incremented according to the following rules:
 *
 * <ul>
 *   <li>When a cache lookup encounters an existing cache entry, {@code hitCount} is incremented.
 *   <li>When a cache lookup first encounters a missing cache entry, a new entry is loaded, and
 *       {@code missCount} and {@code loadSuccessCount} are incremented.
 *   <li>When an exception is thrown while loading a missing cache entry, {@code missCount} and
 *       {@code loadFailureCount} are incremented.
 *   <li>When an entry is evicted from the cache, {@code evictionCount} is incremented.
 * </ul>
 */
public class CacheStats {
    private final long hitCount;
    private final long missCount;
    private final long loadSuccessCount;
    private final long loadFailureCount;
    private final long totalLoadTime;
    private final long evictionCount;

    /**
     * Constructs a new {@code CacheStats} instance.
     */
    public CacheStats(
            long hitCount,
            long missCount,
            long loadSuccessCount,
            long loadFailureCount,
            long totalLoadTime,
            long evictionCount) {
        this.hitCount = hitCount;
        this.missCount = missCount;
        this.loadSuccessCount = loadSuccessCount;
        this.loadFailureCount = loadFailureCount;
        this.totalLoadTime = totalLoadTime;
        this.evictionCount = evictionCount;
    }

    /**
     * Returns the number of times {@link Cache} lookup methods have returned either a cached or
     * uncached value. This is defined as {@code hitCount + missCount}.
     */
    public long requestCount() {
        return hitCount + missCount;
    }

    /**
     * Returns the number of times {@link Cache} lookup methods have returned a cached value.
     */
    public long hitCount() {
        return hitCount;
    }

    /**
     * Returns the ratio of cache requests which were hits. This is defined as
     * {@code hitCount / requestCount}, or {@code 1.0} when {@code requestCount == 0}.
     */
    public double hitRate() {
        long requestCount = requestCount();
        return (requestCount == 0) ? 1.0 : (double) hitCount / requestCount;
    }

    /**
     * Returns the number of times {@link Cache} lookup methods have returned an uncached (newly
     * loaded) value, or null.
     */
    public long missCount() {
        return missCount;
    }

    /**
     * Returns the ratio of cache requests which were misses. This is defined as
     * {@code missCount / requestCount}, or {@code 0.0} when {@code requestCount == 0}.
     */
    public double missRate() {
        long requestCount = requestCount();
        return (requestCount == 0) ? 0.0 : (double) missCount / requestCount;
    }

    /**
     * Returns the total number of times that {@link Cache} lookup methods attempted to load new
     * values. This includes both successful load operations and those that threw exceptions.
     */
    public long loadCount() {
        return loadSuccessCount + loadFailureCount;
    }

    /**
     * Returns the number of times {@link Cache} lookup methods have successfully loaded a new value.
     */
    public long loadSuccessCount() {
        return loadSuccessCount;
    }

    /**
     * Returns the number of times {@link Cache} lookup methods threw an exception while loading a
     * new value.
     */
    public long loadFailureCount() {
        return loadFailureCount;
    }

    /**
     * Returns the ratio of cache loading attempts which threw exceptions. This is defined as
     * {@code loadFailureCount / (loadSuccessCount + loadFailureCount)}, or {@code 0.0} when
     * {@code loadSuccessCount + loadFailureCount == 0}.
     */
    public double loadFailureRate() {
        long totalLoadCount = loadSuccessCount + loadFailureCount;
        return (totalLoadCount == 0) ? 0.0 : (double) loadFailureCount / totalLoadCount;
    }

    /**
     * Returns the total number of nanoseconds the cache has spent loading new values.
     */
    public long totalLoadTime() {
        return totalLoadTime;
    }

    /**
     * Returns the average time spent loading new values. This is defined as
     * {@code totalLoadTime / (loadSuccessCount + loadFailureCount)}.
     */
    public double averageLoadPenalty() {
        long totalLoadCount = loadSuccessCount + loadFailureCount;
        return (totalLoadCount == 0) ? 0.0 : (double) totalLoadTime / totalLoadCount;
    }

    /**
     * Returns the number of times an entry has been evicted.
     */
    public long evictionCount() {
        return evictionCount;
    }

    /**
     * Returns a new {@code CacheStats} representing the difference between this {@code CacheStats}
     * and {@code other}.
     */
    public CacheStats minus(CacheStats other) {
        return new CacheStats(
                Math.max(0, hitCount - other.hitCount),
                Math.max(0, missCount - other.missCount),
                Math.max(0, loadSuccessCount - other.loadSuccessCount),
                Math.max(0, loadFailureCount - other.loadFailureCount),
                Math.max(0, totalLoadTime - other.totalLoadTime),
                Math.max(0, evictionCount - other.evictionCount));
    }

    /**
     * Returns a new {@code CacheStats} representing the sum of this {@code CacheStats} and
     * {@code other}.
     */
    public CacheStats plus(CacheStats other) {
        return new CacheStats(
                hitCount + other.hitCount,
                missCount + other.missCount,
                loadSuccessCount + other.loadSuccessCount,
                loadFailureCount + other.loadFailureCount,
                totalLoadTime + other.totalLoadTime,
                evictionCount + other.evictionCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hitCount, missCount, loadSuccessCount, loadFailureCount, totalLoadTime, evictionCount);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof CacheStats)) {
            return false;
        }
        CacheStats other = (CacheStats) obj;
        return hitCount == other.hitCount
                && missCount == other.missCount
                && loadSuccessCount == other.loadSuccessCount
                && loadFailureCount == other.loadFailureCount
                && totalLoadTime == other.totalLoadTime
                && evictionCount == other.evictionCount;
    }

    @Override
    public String toString() {
        return "CacheStats{"
                + "hitCount=" + hitCount
                + ", missCount=" + missCount
                + ", loadSuccessCount=" + loadSuccessCount
                + ", loadFailureCount=" + loadFailureCount
                + ", totalLoadTime=" + totalLoadTime
                + ", evictionCount=" + evictionCount
                + ", hitRate=" + String.format("%.2f%%", hitRate() * 100)
                + '}';
    }
}
