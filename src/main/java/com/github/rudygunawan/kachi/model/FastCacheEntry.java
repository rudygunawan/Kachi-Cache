package com.github.rudygunawan.kachi.model;

/**
 * Lightweight cache entry optimized for HighPerformanceCache.
 *
 * <p>Differences from standard CacheEntry:
 * <ul>
 *   <li>No AtomicLong objects (saves ~300ns per creation)</li>
 *   <li>Volatile long for access time (lock-free read/write)</li>
 *   <li>No access count tracking (not needed for random eviction)</li>
 *   <li>No refresh time tracking (for refresh-enabled caches only)</li>
 * </ul>
 *
 * <p>Performance: ~100-150ns to create (vs ~450ns for full CacheEntry)
 *
 * @param <V> the type of the cached value
 */
public class FastCacheEntry<V> {
    // Minimum time an entry must stay in cache before eligible for eviction (1 second)
    private static final long MIN_EVICTION_AGE_NANOS = 1_000_000_000L;

    private final V value;
    private final long writeTime;
    private final long expirationTime;
    private final int weight;
    private volatile long accessTime;  // Volatile instead of AtomicLong!

    /**
     * Creates a new fast cache entry.
     *
     * @param value the value to cache
     * @param ttlNanos the time-to-live in nanoseconds, or 0 for no expiration
     * @param weight the weight of this entry
     * @param currentTime current time from System.nanoTime() (passed in to avoid extra calls)
     */
    public FastCacheEntry(V value, long ttlNanos, int weight, long currentTime) {
        this.value = value;
        this.writeTime = currentTime;
        this.expirationTime = ttlNanos > 0 ? currentTime + ttlNanos : Long.MAX_VALUE;
        this.accessTime = currentTime;
        this.weight = weight;
    }

    /**
     * Returns the cached value.
     */
    public V getValue() {
        return value;
    }

    /**
     * Returns the time (in nanoseconds) when this entry was written.
     */
    public long getWriteTime() {
        return writeTime;
    }

    /**
     * Returns the time (in nanoseconds) when this entry expires.
     */
    public long getExpirationTime() {
        return expirationTime;
    }

    /**
     * Returns the time (in nanoseconds) when this entry was last accessed.
     */
    public long getAccessTime() {
        return accessTime;
    }

    /**
     * Returns the weight of this entry.
     */
    public int getWeight() {
        return weight;
    }

    /**
     * Updates the access time to the current time.
     * Uses volatile write - no CAS needed for performance cache.
     */
    public void updateAccessTime(long currentTime) {
        this.accessTime = currentTime;
    }

    /**
     * Checks if this entry has expired.
     */
    public boolean isExpired(long currentTime) {
        return currentTime >= expirationTime;
    }

    /**
     * Checks if this entry is eligible for eviction.
     * Entries must be at least 1 second old to prevent thrashing.
     */
    public boolean isEligibleForEviction(long currentTime) {
        return (currentTime - writeTime) >= MIN_EVICTION_AGE_NANOS;
    }

    // Not needed for HighPerformanceCache:
    // - getAccessCount() - random eviction doesn't need frequency
    // - getLastRefreshTime() - only for refresh-enabled caches
    // - updateLastRefreshTime() - only for refresh-enabled caches
}
