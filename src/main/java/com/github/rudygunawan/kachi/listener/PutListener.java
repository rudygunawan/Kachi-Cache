package com.github.rudygunawan.kachi.listener;

import com.github.rudygunawan.kachi.policy.PutCause;

/**
 * A listener for cache put/insert operations.
 * <p>
 * This listener is invoked whenever an entry is successfully put into the cache,
 * allowing applications to react to cache insertions and updates. The {@link PutCause}
 * parameter distinguishes between new insertions (INSERT) and updates to existing
 * entries (UPDATE).
 *
 * <p><b>Common Use Cases:</b>
 * <ul>
 *   <li>Async database upsert operations - sync cache with persistent storage</li>
 *   <li>Cache write-through patterns - propagate writes to downstream systems</li>
 *   <li>Audit logging - track all cache modifications</li>
 *   <li>Metrics and monitoring - measure cache write patterns</li>
 *   <li>Event streaming - publish cache events to message queues</li>
 * </ul>
 *
 * <p><b>Threading and Performance:</b>
 * <ul>
 *   <li>The listener is invoked synchronously during the put operation</li>
 *   <li>Listener execution time directly impacts put() performance</li>
 *   <li>For long-running operations (like database writes), use async processing:
 *       <pre>{@code
 *       .putListener((key, value, cause) -> {
 *           if (cause.isNewEntry()) {
 *               executor.submit(() -> database.insert(key, value));
 *           }
 *       })
 *       }</pre>
 *   </li>
 *   <li>Exceptions thrown by the listener are logged and swallowed to prevent
 *       cache operation failures</li>
 * </ul>
 *
 * <p><b>Example - Async Database Upsert:</b>
 * <pre>{@code
 * Cache<String, User> cache = CacheBuilder.newBuilder()
 *     .maximumSize(1000)
 *     .putListener((key, user, cause) -> {
 *         // Async upsert to database
 *         asyncExecutor.submit(() -> {
 *             if (cause == PutCause.INSERT) {
 *                 userRepository.insert(key, user);
 *             } else {
 *                 userRepository.update(key, user);
 *             }
 *         });
 *     })
 *     .build();
 *
 * // Put operations trigger the listener
 * cache.put("user123", new User("John Doe")); // Triggers INSERT
 * cache.put("user123", new User("Jane Doe")); // Triggers UPDATE
 * }</pre>
 *
 * <p><b>Example - Write-Through Cache:</b>
 * <pre>{@code
 * Cache<String, Config> cache = CacheBuilder.newBuilder()
 *     .maximumSize(100)
 *     .putListener((key, config, cause) -> {
 *         // Synchronous write-through to config service
 *         configService.save(key, config);
 *     })
 *     .build();
 * }</pre>
 *
 * <p><b>Example - Conditional Processing:</b>
 * <pre>{@code
 * Cache<Long, Product> cache = CacheBuilder.newBuilder()
 *     .maximumSize(5000)
 *     .putListener((id, product, cause) -> {
 *         // Only process new entries
 *         if (cause.isNewEntry()) {
 *             searchIndex.addDocument(id, product);
 *             analytics.trackNewProduct(product);
 *         }
 *     })
 *     .build();
 * }</pre>
 *
 * @param <K> the type of keys maintained by the cache
 * @param <V> the type of cached values
 * @see PutCause
 * @see RemovalListener
 * @since 1.0.0
 */
@FunctionalInterface
public interface PutListener<K, V> {

    /**
     * Notifies the listener that an entry has been put into the cache.
     * <p>
     * This method is called synchronously during the put operation, after the
     * entry has been successfully stored in the cache but before any eviction
     * checks are performed.
     *
     * <p><b>Important:</b> Any exception thrown by this method will be logged
     * and swallowed to prevent the put operation from failing. The cache operation
     * will complete successfully regardless of listener behavior.
     *
     * @param key   the key of the entry that was put (never null)
     * @param value the value that was put (never null)
     * @param cause the reason for the put operation - INSERT for new entries,
     *              UPDATE for replacements (never null)
     */
    void onPut(K key, V value, PutCause cause);
}
