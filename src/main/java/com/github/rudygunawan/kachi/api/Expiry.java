package com.github.rudygunawan.kachi.api;

/**
 * Custom expiration policy that allows different expiration times for individual cache entries.
 *
 * <p>Expiration times are represented as durations in nanoseconds. A return value of 0 means
 * the entry should expire immediately. A return value of Long.MAX_VALUE means the entry should
 * never expire.
 *
 * <p>This interface provides fine-grained control over entry expiration, allowing each entry
 * to have its own TTL based on its key, value, or other application-specific logic.
 *
 * <p><b>Important:</b> The methods in this interface are called synchronously during cache
 * operations, so implementations should be fast and non-blocking.
 *
 * <p>Usage example:
 * <pre>{@code
 * Expiry<String, User> expiry = new Expiry<String, User>() {
 *   public long expireAfterCreate(String key, User user, long currentTime) {
 *     // Premium users get longer cache time
 *     return user.isPremium()
 *         ? TimeUnit.HOURS.toNanos(2)
 *         : TimeUnit.MINUTES.toNanos(30);
 *   }
 *
 *   public long expireAfterUpdate(String key, User user,
 *                                 long currentTime, long currentDuration) {
 *     // Keep same duration on update
 *     return currentDuration;
 *   }
 *
 *   public long expireAfterRead(String key, User user,
 *                               long currentTime, long currentDuration) {
 *     // Extend expiration on read
 *     return TimeUnit.MINUTES.toNanos(30);
 *   }
 * };
 *
 * Cache<String, User> cache = CacheBuilder.newBuilder()
 *     .expireAfter(expiry)
 *     .build();
 * }</pre>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public interface Expiry<K, V> {

    /**
     * Specifies that the entry should be automatically removed from the cache once the duration
     * has elapsed after the entry's creation. The duration is calculated based on the key, value,
     * and current time.
     *
     * @param key the key of the entry being created
     * @param value the value of the entry being created
     * @param currentTime the current time in nanoseconds (from System.nanoTime())
     * @return the duration in nanoseconds until the entry should expire, or Long.MAX_VALUE for no expiration
     */
    long expireAfterCreate(K key, V value, long currentTime);

    /**
     * Specifies that the entry should be automatically removed from the cache once the duration
     * has elapsed after the entry's value was last updated. The duration is calculated based on
     * the key, value, current time, and the current duration.
     *
     * <p>This method is called when an existing entry's value is updated via put() or other
     * modification operations.
     *
     * @param key the key of the entry being updated
     * @param value the new value of the entry
     * @param currentTime the current time in nanoseconds (from System.nanoTime())
     * @param currentDuration the current duration in nanoseconds until expiration
     * @return the duration in nanoseconds until the entry should expire, or Long.MAX_VALUE for no expiration
     */
    long expireAfterUpdate(K key, V value, long currentTime, long currentDuration);

    /**
     * Specifies that the entry should be automatically removed from the cache once the duration
     * has elapsed after the entry was last accessed (read). The duration is calculated based on
     * the key, value, current time, and the current duration.
     *
     * <p>This method is called when an entry is read via get() or other read operations.
     *
     * <p><b>Note:</b> This method is only called when the cache is configured to track access time
     * (i.e., when using custom expiry). It is similar to expireAfterAccess() but allows per-entry
     * customization.
     *
     * @param key the key of the entry being accessed
     * @param value the value of the entry being accessed
     * @param currentTime the current time in nanoseconds (from System.nanoTime())
     * @param currentDuration the current duration in nanoseconds until expiration
     * @return the duration in nanoseconds until the entry should expire, or Long.MAX_VALUE for no expiration
     */
    long expireAfterRead(K key, V value, long currentTime, long currentDuration);
}
