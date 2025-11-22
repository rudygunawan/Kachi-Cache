package com.github.rudygunawan.kachi.listener;

import com.github.rudygunawan.kachi.policy.RemovalCause;

/**
 * A writer for cache entries, providing structured write-through and write-back operations
 * to external storage (e.g., databases, distributed caches, file systems).
 *
 * <p>CacheWriter is invoked synchronously during cache operations and provides separation
 * between cache write operations and removal operations, unlike {@link PutListener} which
 * only handles puts.
 *
 * <p><b>Key differences from PutListener:</b>
 * <ul>
 *   <li>CacheWriter has separate write() and delete() methods</li>
 *   <li>delete() is called for all removals (eviction, expiration, explicit invalidation)</li>
 *   <li>write() is called for both inserts and updates (no distinction)</li>
 *   <li>More aligned with database/storage semantics</li>
 * </ul>
 *
 * <p><b>Write-Through Pattern:</b>
 * <pre>{@code
 * CacheWriter<String, User> writer = new CacheWriter<String, User>() {
 *   @Override
 *   public void write(String key, User value) {
 *     database.upsert(key, value);  // Synchronous write
 *   }
 *
 *   @Override
 *   public void delete(String key, User value, RemovalCause cause) {
 *     if (cause == RemovalCause.EXPLICIT) {
 *       database.delete(key);  // Only delete on explicit invalidation
 *     }
 *   }
 * };
 *
 * Cache<String, User> cache = CacheBuilder.newBuilder()
 *     .maximumSize(1000)
 *     .writer(writer)
 *     .build();
 * }</pre>
 *
 * <p><b>Async Write-Behind Pattern:</b>
 * <pre>{@code
 * ExecutorService writeExecutor = Executors.newFixedThreadPool(4);
 *
 * CacheWriter<String, User> writer = new CacheWriter<String, User>() {
 *   @Override
 *   public void write(String key, User value) {
 *     writeExecutor.submit(() -> database.upsert(key, value));
 *   }
 *
 *   @Override
 *   public void delete(String key, User value, RemovalCause cause) {
 *     writeExecutor.submit(() -> database.delete(key));
 *   }
 * };
 * }</pre>
 *
 * <p><b>Selective Write Pattern:</b>
 * <pre>{@code
 * CacheWriter<String, Config> writer = new CacheWriter<String, Config>() {
 *   @Override
 *   public void write(String key, Config value) {
 *     if (value.isPersistent()) {
 *       configStore.save(key, value);
 *     }
 *   }
 *
 *   @Override
 *   public void delete(String key, Config value, RemovalCause cause) {
 *     // Only delete on explicit invalidation, not on eviction
 *     if (cause == RemovalCause.EXPLICIT) {
 *       configStore.delete(key);
 *     }
 *   }
 * };
 * }</pre>
 *
 * <p><b>Threading and Performance:</b>
 * <ul>
 *   <li>The writer is invoked synchronously during cache operations</li>
 *   <li>Writer execution time directly impacts cache operation latency</li>
 *   <li>For long-running writes, use async executors (write-behind pattern)</li>
 *   <li>Exceptions thrown by the writer are logged and swallowed</li>
 * </ul>
 *
 * <p><b>Comparison with other listeners:</b>
 * <table border="1">
 *   <tr>
 *     <th>Listener</th>
 *     <th>Write Events</th>
 *     <th>Delete Events</th>
 *     <th>Use Case</th>
 *   </tr>
 *   <tr>
 *     <td>PutListener</td>
 *     <td>INSERT, UPDATE</td>
 *     <td>No</td>
 *     <td>Async upsert, distinguish new vs update</td>
 *   </tr>
 *   <tr>
 *     <td>CacheWriter</td>
 *     <td>All writes</td>
 *     <td>All removals with cause</td>
 *     <td>Write-through, write-back, database sync</td>
 *   </tr>
 *   <tr>
 *     <td>RemovalListener</td>
 *     <td>No</td>
 *     <td>All removals with cause</td>
 *     <td>Cleanup, resource release</td>
 *   </tr>
 * </table>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @see PutListener
 * @see RemovalListener
 * @since 1.0.0
 */
public interface CacheWriter<K, V> {

    /**
     * Writes a cache entry to external storage. This method is called synchronously during
     * cache put operations (both inserts and updates).
     *
     * <p>This method should implement the write-through or write-behind logic to persist
     * the cache entry to external storage.
     *
     * <p><b>Important:</b> Any exception thrown by this method will be logged and swallowed
     * to prevent the cache operation from failing. The cache update will complete successfully
     * regardless of writer behavior.
     *
     * @param key the key being written (never null)
     * @param value the value being written (never null)
     */
    void write(K key, V value);

    /**
     * Deletes a cache entry from external storage. This method is called synchronously when
     * an entry is removed from the cache for any reason (eviction, expiration, explicit
     * invalidation).
     *
     * <p>The {@code cause} parameter indicates why the entry was removed:
     * <ul>
     *   <li>{@link RemovalCause#EXPLICIT} - User called invalidate()</li>
     *   <li>{@link RemovalCause#REPLACED} - Entry was replaced by a new put()</li>
     *   <li>{@link RemovalCause#SIZE} - Entry was evicted due to size constraints</li>
     *   <li>{@link RemovalCause#EXPIRED} - Entry expired due to TTL</li>
     * </ul>
     *
     * <p>Implementations may choose to delete from storage only for certain causes. For
     * example, only deleting on {@code EXPLICIT} invalidation but not on eviction.
     *
     * <p><b>Important:</b> Any exception thrown by this method will be logged and swallowed
     * to prevent the cache operation from failing.
     *
     * @param key the key being deleted (never null)
     * @param value the value being deleted (never null)
     * @param cause the reason for the removal (never null)
     */
    void delete(K key, V value, RemovalCause cause);

    /**
     * Creates a CacheWriter that performs both write and delete operations synchronously.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * CacheWriter<String, User> writer = CacheWriter.sync(
     *     (key, value) -> database.upsert(key, value),
     *     (key, value, cause) -> {
     *       if (cause == RemovalCause.EXPLICIT) {
     *         database.delete(key);
     *       }
     *     }
     * );
     * }</pre>
     *
     * @param writeAction the action to perform on write
     * @param deleteAction the action to perform on delete
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return a CacheWriter implementation
     */
    static <K, V> CacheWriter<K, V> sync(
            java.util.function.BiConsumer<K, V> writeAction,
            TriConsumer<K, V, RemovalCause> deleteAction) {
        return new CacheWriter<K, V>() {
            @Override
            public void write(K key, V value) {
                writeAction.accept(key, value);
            }

            @Override
            public void delete(K key, V value, RemovalCause cause) {
                deleteAction.accept(key, value, cause);
            }
        };
    }

    /**
     * Creates a CacheWriter that performs write and delete operations asynchronously
     * using the provided executor.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * ExecutorService executor = Executors.newFixedThreadPool(4);
     *
     * CacheWriter<String, User> writer = CacheWriter.async(
     *     executor,
     *     (key, value) -> database.upsert(key, value),
     *     (key, value, cause) -> database.delete(key)
     * );
     * }</pre>
     *
     * @param executor the executor to use for async operations
     * @param writeAction the action to perform on write
     * @param deleteAction the action to perform on delete
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return a CacheWriter implementation
     */
    static <K, V> CacheWriter<K, V> async(
            java.util.concurrent.Executor executor,
            java.util.function.BiConsumer<K, V> writeAction,
            TriConsumer<K, V, RemovalCause> deleteAction) {
        return new CacheWriter<K, V>() {
            @Override
            public void write(K key, V value) {
                executor.execute(() -> writeAction.accept(key, value));
            }

            @Override
            public void delete(K key, V value, RemovalCause cause) {
                executor.execute(() -> deleteAction.accept(key, value, cause));
            }
        };
    }

    /**
     * Functional interface for tri-consumer (three parameters).
     */
    @FunctionalInterface
    interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }
}
