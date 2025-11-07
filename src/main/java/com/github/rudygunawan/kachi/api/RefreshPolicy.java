package com.github.rudygunawan.kachi.api;

/**
 * Custom refresh policy that determines when cache entries should be automatically refreshed
 * in the background. This allows entries to be proactively reloaded before they expire,
 * preventing cache stampede and ensuring fresh data.
 *
 * <p>Refresh operations are performed asynchronously using the {@link CacheLoader}, so the old
 * value continues to be served while the new value is being loaded. If the refresh fails, the
 * old value is retained.
 *
 * <p>This is particularly useful for:
 * <ul>
 *   <li>High-traffic entries that would cause cache stampede if they expired
 *   <li>Data that changes on a predictable schedule (e.g., stock prices during market hours)
 *   <li>Expensive-to-compute values that should always be warm in the cache
 * </ul>
 *
 * <p><b>Important:</b> Refresh policies only work with {@link LoadingCache} instances that
 * have a {@link CacheLoader} configured. Regular {@link Cache} instances will ignore refresh
 * policies.
 *
 * <p>The methods in this interface are called frequently, so implementations should be fast
 * and non-blocking.
 *
 * <p>Usage example:
 * <pre>{@code
 * RefreshPolicy<String, StockPrice> policy = new RefreshPolicy<String, StockPrice>() {
 *   public long getRefreshInterval(String key, StockPrice value, long currentTime) {
 *     // Refresh every minute during market hours, every 10 minutes otherwise
 *     return isMarketHours(currentTime)
 *         ? TimeUnit.MINUTES.toNanos(1)
 *         : TimeUnit.MINUTES.toNanos(10);
 *   }
 * };
 *
 * LoadingCache<String, StockPrice> cache = CacheBuilder.newBuilder()
 *     .refreshAfter(policy)
 *     .build(key -> loadStockPrice(key));
 * }</pre>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public interface RefreshPolicy<K, V> {

    /**
     * Returns the duration in nanoseconds after which an entry should be automatically refreshed.
     * This method is called each time the refresh scheduler checks an entry.
     *
     * <p>The refresh interval can be different for each entry and can change over time based on
     * the current time, key, or value. For example, you might want to refresh certain entries
     * more frequently during business hours.
     *
     * <p>Return values:
     * <ul>
     *   <li>Positive value: Entry will be refreshed after this many nanoseconds
     *   <li>Zero: Entry should be refreshed immediately
     *   <li>Long.MAX_VALUE: Entry should never be automatically refreshed
     * </ul>
     *
     * <p><b>Note:</b> The refresh is performed asynchronously in the background. The old value
     * continues to be served until the new value is loaded successfully.
     *
     * @param key the key of the entry
     * @param value the current value of the entry
     * @param currentTime the current time in nanoseconds (from System.nanoTime())
     * @return the duration in nanoseconds until the entry should be refreshed, or Long.MAX_VALUE to disable refresh
     */
    long getRefreshInterval(K key, V value, long currentTime);

    /**
     * Called when a refresh completes successfully. This allows the policy to react to refresh
     * events, such as adjusting future refresh intervals based on the new value.
     *
     * <p>This method is optional. The default implementation does nothing.
     *
     * @param key the key of the refreshed entry
     * @param oldValue the old value before refresh
     * @param newValue the new value after refresh
     * @param currentTime the current time in nanoseconds
     */
    default void onRefreshSuccess(K key, V oldValue, V newValue, long currentTime) {
        // Default: no action
    }

    /**
     * Called when a refresh fails with an exception. This allows the policy to react to failures,
     * such as adjusting future refresh intervals or logging errors.
     *
     * <p>This method is optional. The default implementation does nothing.
     *
     * <p><b>Note:</b> When a refresh fails, the old value is retained in the cache.
     *
     * @param key the key of the entry that failed to refresh
     * @param value the current value (old value that will be retained)
     * @param error the exception that caused the refresh to fail
     * @param currentTime the current time in nanoseconds
     */
    default void onRefreshFailure(K key, V value, Throwable error, long currentTime) {
        // Default: no action
    }
}
