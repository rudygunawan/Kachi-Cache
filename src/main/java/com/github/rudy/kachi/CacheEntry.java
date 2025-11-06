package com.github.rudy.kachi;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A cache entry that wraps a value with metadata for expiration and access tracking.
 *
 * @param <V> the type of the cached value
 */
class CacheEntry<V> {
    private final V value;
    private final long writeTime;
    private final long expirationTime;
    private final AtomicLong accessTime;
    private final AtomicLong accessCount;

    /**
     * Creates a new cache entry with the specified value and expiration.
     *
     * @param value the value to cache
     * @param ttlNanos the time-to-live in nanoseconds, or 0 for no expiration
     */
    CacheEntry(V value, long ttlNanos) {
        this.value = value;
        this.writeTime = System.nanoTime();
        this.expirationTime = ttlNanos > 0 ? writeTime + ttlNanos : Long.MAX_VALUE;
        this.accessTime = new AtomicLong(writeTime);
        this.accessCount = new AtomicLong(0);
    }

    /**
     * Returns the cached value.
     */
    V getValue() {
        return value;
    }

    /**
     * Returns the time (in nanoseconds) when this entry was written.
     */
    long getWriteTime() {
        return writeTime;
    }

    /**
     * Returns the time (in nanoseconds) when this entry expires.
     */
    long getExpirationTime() {
        return expirationTime;
    }

    /**
     * Returns the time (in nanoseconds) when this entry was last accessed.
     */
    long getAccessTime() {
        return accessTime.get();
    }

    /**
     * Updates the access time to the current time and increments access count.
     */
    void updateAccessTime() {
        accessTime.set(System.nanoTime());
        accessCount.incrementAndGet();
    }

    /**
     * Returns the number of times this entry has been accessed.
     */
    long getAccessCount() {
        return accessCount.get();
    }

    /**
     * Returns true if this entry has expired.
     */
    boolean isExpired() {
        return System.nanoTime() >= expirationTime;
    }

    /**
     * Returns true if this entry has expired after the specified time.
     */
    boolean isExpiredAfter(long timeNanos) {
        return timeNanos >= expirationTime;
    }
}
