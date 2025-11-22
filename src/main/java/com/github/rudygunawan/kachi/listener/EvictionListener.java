package com.github.rudygunawan.kachi.listener;

import com.github.rudygunawan.kachi.policy.RemovalCause;

/**
 * A listener for cache eviction events - entries removed due to size constraints or expiration.
 *
 * <p>This listener is a specialized version of {@link RemovalListener} that only receives
 * notifications for automatic evictions (SIZE and EXPIRED causes), not for explicit invalidations
 * or replacements. This is useful when you need to react specifically to capacity-driven removals.
 *
 * <p><b>Key Differences from RemovalListener:</b>
 * <table border="1">
 *   <tr>
 *     <th>Listener</th>
 *     <th>SIZE</th>
 *     <th>EXPIRED</th>
 *     <th>EXPLICIT</th>
 *     <th>REPLACED</th>
 *   </tr>
 *   <tr>
 *     <td>EvictionListener</td>
 *     <td>✓</td>
 *     <td>✓</td>
 *     <td>✗</td>
 *     <td>✗</td>
 *   </tr>
 *   <tr>
 *     <td>RemovalListener</td>
 *     <td>✓</td>
 *     <td>✓</td>
 *     <td>✓</td>
 *     <td>✓</td>
 *   </tr>
 * </table>
 *
 * <p><b>Caffeine Compatibility:</b> In Caffeine, EvictionListener is synchronous and always runs
 * inline. Kachi follows the same pattern - this listener is invoked synchronously during eviction.
 *
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Resource cleanup for evicted entries (close files, release locks)</li>
 *   <li>Metrics tracking for cache pressure</li>
 *   <li>Logging eviction events for analysis</li>
 *   <li>Secondary cache population (multi-level caching)</li>
 * </ul>
 *
 * <p><b>Threading and Performance:</b>
 * <ul>
 *   <li>The listener is invoked synchronously during eviction</li>
 *   <li>Listener execution time impacts eviction performance</li>
 *   <li>Keep eviction listener logic fast and simple</li>
 *   <li>For slow operations, delegate to async executor</li>
 *   <li>Exceptions thrown by the listener are logged and swallowed</li>
 * </ul>
 *
 * <p><b>Example - Resource Cleanup:</b>
 * <pre>{@code
 * Cache<String, FileHandle> cache = CacheBuilder.newBuilder()
 *     .maximumSize(100)
 *     .evictionListener((key, handle, cause) -> {
 *         if (cause == RemovalCause.SIZE) {
 *             handle.close();  // Clean up when evicted due to size
 *         }
 *     })
 *     .build();
 * }</pre>
 *
 * <p><b>Example - Multi-Level Cache:</b>
 * <pre>{@code
 * Cache<String, Data> l1Cache = CacheBuilder.newBuilder()
 *     .maximumSize(100)
 *     .evictionListener((key, data, cause) -> {
 *         // Move to L2 cache when evicted from L1
 *         l2Cache.put(key, data);
 *     })
 *     .build();
 * }</pre>
 *
 * <p><b>Example - Metrics Tracking:</b>
 * <pre>{@code
 * AtomicLong sizeEvictions = new AtomicLong();
 * AtomicLong expiryEvictions = new AtomicLong();
 *
 * Cache<String, User> cache = CacheBuilder.newBuilder()
 *     .maximumSize(1000)
 *     .expireAfterWrite(10, TimeUnit.MINUTES)
 *     .evictionListener((key, user, cause) -> {
 *         if (cause == RemovalCause.SIZE) {
 *             sizeEvictions.incrementAndGet();
 *         } else if (cause == RemovalCause.EXPIRED) {
 *             expiryEvictions.incrementAndGet();
 *         }
 *     })
 *     .build();
 * }</pre>
 *
 * <p><b>Example - Async Processing:</b>
 * <pre>{@code
 * ExecutorService cleanupExecutor = Executors.newFixedThreadPool(2);
 *
 * Cache<String, Resource> cache = CacheBuilder.newBuilder()
 *     .maximumSize(100)
 *     .evictionListener((key, resource, cause) -> {
 *         // Delegate slow cleanup to executor
 *         cleanupExecutor.submit(() -> resource.expensiveCleanup());
 *     })
 *     .build();
 * }</pre>
 *
 * <p><b>Combining with RemovalListener:</b>
 * <pre>{@code
 * Cache<String, User> cache = CacheBuilder.newBuilder()
 *     .maximumSize(1000)
 *     .evictionListener((key, user, cause) -> {
 *         // Only for SIZE and EXPIRED
 *         logger.warn("Evicted {}: {}", key, cause);
 *     })
 *     .removalListener((key, user, cause) -> {
 *         // For ALL removals (SIZE, EXPIRED, EXPLICIT, REPLACED)
 *         metrics.recordRemoval(cause);
 *     })
 *     .build();
 * }</pre>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @see RemovalListener
 * @see RemovalCause#wasEvicted()
 * @since 1.0.0
 */
@FunctionalInterface
public interface EvictionListener<K, V> {

    /**
     * Notifies the listener that an entry has been evicted from the cache.
     *
     * <p>This method is called synchronously during eviction, after the entry has been removed
     * from the cache. The {@code cause} parameter will always be either {@link RemovalCause#SIZE}
     * or {@link RemovalCause#EXPIRED}.
     *
     * <p><b>Important:</b> Any exception thrown by this method will be logged and swallowed
     * to prevent the eviction operation from failing. The cache operation will complete
     * successfully regardless of listener behavior.
     *
     * @param key the key of the evicted entry (never null)
     * @param value the value of the evicted entry (never null)
     * @param cause the reason for eviction - either SIZE or EXPIRED (never null)
     */
    void onEviction(K key, V value, RemovalCause cause);
}
