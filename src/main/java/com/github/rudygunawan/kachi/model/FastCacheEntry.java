package com.github.rudygunawan.kachi.model;

import com.github.rudygunawan.kachi.policy.Strength;
import com.github.rudygunawan.kachi.reference.ValueReference;

import java.lang.ref.ReferenceQueue;

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
 * <p><b>Reference Strength Support:</b>
 * <p>Supports strong, weak, and soft value references for GC-aware caching.
 *
 * @param <V> the type of the cached value
 */
public class FastCacheEntry<V> {
    // Minimum time an entry must stay in cache before eligible for eviction (1 second)
    private static final long MIN_EVICTION_AGE_NANOS = 1_000_000_000L;

    private final ValueReference<V> valueRef;
    private final long writeTime;
    private final long expirationTime;
    private final int weight;
    private volatile long accessTime;  // Volatile instead of AtomicLong!

    /**
     * Creates a new fast cache entry with strong value reference (default).
     *
     * @param value the value to cache
     * @param ttlNanos the time-to-live in nanoseconds, or 0 for no expiration
     * @param weight the weight of this entry
     * @param currentTime current time from System.nanoTime() (passed in to avoid extra calls)
     */
    public FastCacheEntry(V value, long ttlNanos, int weight, long currentTime) {
        this(value, ttlNanos, weight, currentTime, Strength.STRONG, null);
    }

    /**
     * Creates a new fast cache entry with configurable value reference strength.
     *
     * @param value the value to cache
     * @param ttlNanos the time-to-live in nanoseconds, or 0 for no expiration
     * @param weight the weight of this entry
     * @param currentTime current time from System.nanoTime() (passed in to avoid extra calls)
     * @param valueStrength the reference strength for the value
     * @param valueQueue the reference queue for GC notifications (null for STRONG)
     */
    public FastCacheEntry(V value, long ttlNanos, int weight, long currentTime,
                          Strength valueStrength, ReferenceQueue<V> valueQueue) {
        this.valueRef = ValueReference.create(value, valueStrength, valueQueue);
        this.writeTime = currentTime;
        this.expirationTime = ttlNanos > 0 ? currentTime + ttlNanos : Long.MAX_VALUE;
        this.accessTime = currentTime;
        this.weight = weight;
    }

    /**
     * Returns the cached value, or null if it has been garbage collected.
     *
     * @return the value, or null if the value was collected by GC
     */
    public V getValue() {
        return valueRef.get();
    }

    /**
     * Checks if the value reference has been cleared by GC.
     *
     * @return true if the value was collected, false otherwise
     */
    public boolean isValueCleared() {
        return valueRef.isCleared();
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
