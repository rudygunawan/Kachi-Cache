package com.github.rudygunawan.kachi.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A cache entry that wraps a value with metadata for expiration and access tracking.
 *
 * @param <V> the type of the cached value
 */
public class CacheEntry<V> {
    // Minimum time an entry must stay in cache before eligible for eviction (1 second)
    // This prevents rapid thrashing while still allowing timely eviction
    private static final long MIN_EVICTION_AGE_NANOS = 1_000_000_000L; // 1 second

    private final V value;
    private final long writeTime;
    private final long expirationTime;
    private final AtomicLong accessTime;
    private final AtomicLong accessCount;
    private final AtomicLong lastRefreshTime;
    private final int weight;

    /**
     * Creates a new cache entry with the specified value and expiration.
     *
     * @param value the value to cache
     * @param ttlNanos the time-to-live in nanoseconds, or 0 for no expiration
     */
    public CacheEntry(V value, long ttlNanos) {
        this(value, ttlNanos, 1);
    }

    /**
     * Creates a new cache entry with the specified value, expiration, and weight.
     *
     * @param value the value to cache
     * @param ttlNanos the time-to-live in nanoseconds, or 0 for no expiration
     * @param weight the weight of this entry for size-based eviction
     */
    public CacheEntry(V value, long ttlNanos, int weight) {
        this.value = value;
        this.writeTime = System.nanoTime();
        this.expirationTime = ttlNanos > 0 ? writeTime + ttlNanos : Long.MAX_VALUE;
        this.accessTime = new AtomicLong(writeTime);
        this.accessCount = new AtomicLong(0);
        this.lastRefreshTime = new AtomicLong(writeTime);
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
        return accessTime.get();
    }

    /**
     * Updates the access time to the current time and increments access count.
     */
    public void updateAccessTime() {
        accessTime.set(System.nanoTime());
        accessCount.incrementAndGet();
    }

    /**
     * Returns the number of times this entry has been accessed.
     */
    public long getAccessCount() {
        return accessCount.get();
    }

    /**
     * Returns true if this entry has expired.
     */
    public boolean isExpired() {
        return System.nanoTime() >= expirationTime;
    }

    /**
     * Returns true if this entry has expired after the specified time.
     */
    public boolean isExpiredAfter(long timeNanos) {
        return timeNanos >= expirationTime;
    }

    /**
     * Returns the age of this entry in nanoseconds since it was written.
     */
    public long getAgeNanos() {
        return System.nanoTime() - writeTime;
    }

    /**
     * Returns true if this entry is old enough to be considered for eviction.
     * Entries must be at least 1 second old before they can be evicted to prevent thrashing.
     */
    public boolean isEligibleForEviction() {
        return getAgeNanos() >= MIN_EVICTION_AGE_NANOS;
    }

    /**
     * Returns the time (in nanoseconds) when this entry was last refreshed.
     */
    public long getLastRefreshTime() {
        return lastRefreshTime.get();
    }

    /**
     * Updates the last refresh time to the current time.
     */
    public void updateLastRefreshTime() {
        lastRefreshTime.set(System.nanoTime());
    }

    /**
     * Returns the weight of this entry.
     */
    public int getWeight() {
        return weight;
    }
}
