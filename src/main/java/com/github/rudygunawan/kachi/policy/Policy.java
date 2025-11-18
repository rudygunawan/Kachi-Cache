package com.github.rudygunawan.kachi.policy;

import java.util.Map;
import java.util.Optional;

/**
 * Access to inspect and modify the cache's operational characteristics.
 *
 * <p>This interface provides runtime introspection and modification capabilities for cache
 * policies, similar to Caffeine's Policy API. It allows you to:
 * <ul>
 *   <li>Inspect current policy settings (max size, eviction policy, etc.)</li>
 *   <li>Modify policies at runtime (resize cache, change TTL)</li>
 *   <li>Query cache state (hottest/coldest entries, entry age)</li>
 * </ul>
 *
 * <p><b>Accessing the Policy:</b>
 * <pre>{@code
 * Cache<String, User> cache = CacheBuilder.newBuilder()
 *     .maximumSize(1000)
 *     .build();
 *
 * Policy<String, User> policy = cache.policy();
 *
 * // Inspect eviction policy
 * policy.eviction().ifPresent(eviction -> {
 *     System.out.println("Max size: " + eviction.getMaximum());
 *     System.out.println("Current size: " + eviction.weightedSize());
 * });
 *
 * // Dynamically resize
 * policy.eviction().ifPresent(eviction -> {
 *     eviction.setMaximum(2000);
 * });
 * }</pre>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @since 1.0.0
 */
public interface Policy<K, V> {

    /**
     * Returns access to perform operations based on the size-based eviction policy.
     *
     * <p>If the cache was not configured with size-based eviction, this method returns
     * {@link Optional#empty()}.
     *
     * @return access to perform operations based on the eviction policy
     */
    Optional<Eviction<K, V>> eviction();

    /**
     * Returns access to perform operations based on the time-based expiration policy.
     *
     * <p>If the cache was not configured with time-based expiration, this method returns
     * {@link Optional#empty()}.
     *
     * @return access to perform operations based on the expiration policy
     */
    Optional<Expiration<K, V>> expiration();

    /**
     * Eviction policy for size-based constraints.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     */
    interface Eviction<K, V> {

        /**
         * Returns the maximum weighted size of the cache.
         *
         * @return the maximum size (or weight if using weigher)
         */
        long getMaximum();

        /**
         * Sets the maximum weighted size of the cache.
         *
         * <p>This operation allows dynamic resizing of the cache. If the new maximum is smaller
         * than the current size, eviction will occur to bring the cache within the new limit.
         *
         * @param maximum the new maximum size
         * @throws IllegalArgumentException if maximum is negative
         */
        void setMaximum(long maximum);

        /**
         * Returns the current weighted size of the cache.
         *
         * <p>If a weigher is configured, this returns the sum of weights. Otherwise, it returns
         * the number of entries.
         *
         * @return the current weighted size
         */
        long weightedSize();

        /**
         * Returns whether this cache uses a weigher to determine size.
         *
         * @return {@code true} if a weigher is configured, {@code false} otherwise
         */
        boolean isWeighted();

        /**
         * Returns the eviction policy in use.
         *
         * @return the eviction policy (LRU, LFU, FIFO, WINDOW_TINY_LFU)
         */
        EvictionPolicy getEvictionPolicy();

        /**
         * Returns an unmodifiable snapshot map of the entries in this cache that are most
         * likely to be retained (hottest entries). The returned map is ordered from most
         * likely to be retained to least likely, up to the specified limit.
         *
         * <p>This method is useful for debugging and monitoring which entries are being
         * accessed most frequently or recently, depending on the eviction policy.
         *
         * <p><b>Note:</b> The accuracy of this snapshot depends on the cache strategy:
         * <ul>
         *   <li><b>PRECISION</b>: Accurate snapshot based on actual eviction policy</li>
         *   <li><b>HIGH_PERFORMANCE</b>: Best-effort approximation (may use write time)</li>
         * </ul>
         *
         * <p><b>Example usage:</b>
         * <pre>{@code
         * cache.policy().eviction().ifPresent(eviction -> {
         *     Map<K, V> hot = eviction.hottest(10);
         *     System.out.println("Top 10 hottest entries: " + hot.keySet());
         * });
         * }</pre>
         *
         * @param limit the maximum number of entries to return
         * @return an unmodifiable map of the hottest entries, ordered by retention likelihood
         * @throws IllegalArgumentException if limit is negative
         */
        Map<K, V> hottest(int limit);

        /**
         * Returns an unmodifiable snapshot map of the entries in this cache that are most
         * likely to be evicted (coldest entries). The returned map is ordered from most
         * likely to be evicted to least likely, up to the specified limit.
         *
         * <p>This method is useful for debugging and monitoring which entries are candidates
         * for eviction, allowing you to understand eviction behavior.
         *
         * <p><b>Note:</b> The accuracy of this snapshot depends on the cache strategy:
         * <ul>
         *   <li><b>PRECISION</b>: Accurate snapshot based on actual eviction policy</li>
         *   <li><b>HIGH_PERFORMANCE</b>: Best-effort approximation (may use write time)</li>
         * </ul>
         *
         * <p><b>Example usage:</b>
         * <pre>{@code
         * cache.policy().eviction().ifPresent(eviction -> {
         *     Map<K, V> cold = eviction.coldest(10);
         *     System.out.println("Top 10 eviction candidates: " + cold.keySet());
         * });
         * }</pre>
         *
         * @param limit the maximum number of entries to return
         * @return an unmodifiable map of the coldest entries, ordered by eviction likelihood
         * @throws IllegalArgumentException if limit is negative
         */
        Map<K, V> coldest(int limit);
    }

    /**
     * Expiration policy for time-based constraints.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     */
    interface Expiration<K, V> {

        /**
         * Returns the fixed duration used to determine if an entry should be automatically removed
         * due to elapsing this time period after being written, if configured.
         *
         * @return the duration (in nanoseconds) or -1 if not configured
         */
        long getExpiresAfterWrite();

        /**
         * Sets the fixed duration used to determine if an entry should be automatically removed
         * due to elapsing this time period after being written.
         *
         * @param durationNanos the duration in nanoseconds
         * @throws IllegalArgumentException if duration is negative
         */
        void setExpiresAfterWrite(long durationNanos);

        /**
         * Returns the fixed duration used to determine if an entry should be automatically removed
         * due to elapsing this time period after being accessed, if configured.
         *
         * @return the duration (in nanoseconds) or -1 if not configured
         */
        long getExpiresAfterAccess();

        /**
         * Sets the fixed duration used to determine if an entry should be automatically removed
         * due to elapsing this time period after being accessed.
         *
         * @param durationNanos the duration in nanoseconds
         * @throws IllegalArgumentException if duration is negative
         */
        void setExpiresAfterAccess(long durationNanos);

        /**
         * Returns the age of the entry based on the expiration policy, if available.
         *
         * @param key the key for the entry being queried
         * @return the age in nanoseconds, or -1 if not found or not applicable
         */
        long ageOf(K key);

        /**
         * Returns an unmodifiable snapshot map of the entries in this cache that have the
         * youngest expiration times (newest entries). The returned map is ordered from youngest
         * to oldest, up to the specified limit.
         *
         * <p>This method returns entries based on the configured expiration policy:
         * <ul>
         *   <li><b>expireAfterWrite</b>: Entries ordered by creation/write time (newest first)</li>
         *   <li><b>expireAfterAccess</b>: Entries ordered by last access time (most recently accessed first)</li>
         *   <li><b>expireAfter (variable)</b>: Entries ordered by remaining time until expiration</li>
         * </ul>
         *
         * <p><b>Example usage:</b>
         * <pre>{@code
         * cache.policy().expiration().ifPresent(expiration -> {
         *     Map<K, V> youngest = expiration.youngest(10);
         *     System.out.println("Top 10 youngest entries: " + youngest.keySet());
         * });
         * }</pre>
         *
         * @param limit the maximum number of entries to return
         * @return an unmodifiable map of the youngest entries, ordered by age
         * @throws IllegalArgumentException if limit is negative
         */
        Map<K, V> youngest(int limit);

        /**
         * Returns an unmodifiable snapshot map of the entries in this cache that have the
         * oldest expiration times (oldest entries). The returned map is ordered from oldest
         * to youngest, up to the specified limit.
         *
         * <p>This method returns entries based on the configured expiration policy:
         * <ul>
         *   <li><b>expireAfterWrite</b>: Entries ordered by creation/write time (oldest first)</li>
         *   <li><b>expireAfterAccess</b>: Entries ordered by last access time (least recently accessed first)</li>
         *   <li><b>expireAfter (variable)</b>: Entries closest to expiration (soonest to expire first)</li>
         * </ul>
         *
         * <p>This is particularly useful for debugging expiration behavior and understanding
         * which entries are about to expire.
         *
         * <p><b>Example usage:</b>
         * <pre>{@code
         * cache.policy().expiration().ifPresent(expiration -> {
         *     Map<K, V> oldest = expiration.oldest(10);
         *     System.out.println("Top 10 entries closest to expiration: " + oldest.keySet());
         * });
         * }</pre>
         *
         * @param limit the maximum number of entries to return
         * @return an unmodifiable map of the oldest entries, ordered by age
         * @throws IllegalArgumentException if limit is negative
         */
        Map<K, V> oldest(int limit);
    }
}
