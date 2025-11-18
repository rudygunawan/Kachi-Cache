package com.github.rudygunawan.kachi.api;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * A semi-persistent mapping from keys to values with asynchronous operations. Cache entries are
 * manually added and accessed using {@link CompletableFuture} for non-blocking operations.
 *
 * <p>Implementations of this interface are expected to be thread-safe, and can be safely accessed
 * by multiple concurrent threads.
 *
 * <p><b>Example usage:</b>
 * <pre>{@code
 * AsyncCache<String, User> cache = CacheBuilder.newBuilder()
 *     .maximumSize(1000)
 *     .expireAfterWrite(10, TimeUnit.MINUTES)
 *     .buildAsync();
 *
 * // Non-blocking get with async computation
 * CompletableFuture<User> future = cache.get("userId", key -> {
 *     return CompletableFuture.supplyAsync(() -> database.fetchUser(key));
 * });
 *
 * future.thenAccept(user -> System.out.println("Got user: " + user));
 * }</pre>
 *
 * <p><b>Relationship to Cache:</b>
 * <ul>
 *   <li>AsyncCache wraps a synchronous {@link Cache} instance</li>
 *   <li>All operations return {@link CompletableFuture} for non-blocking access</li>
 *   <li>The underlying cache can be accessed via {@link #synchronous()}</li>
 * </ul>
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 * @since 1.0.0
 */
public interface AsyncCache<K, V> {

    /**
     * Returns the value associated with {@code key} in this cache, obtaining that value from
     * {@code mappingFunction} if necessary. This method provides a simple substitute for the
     * conventional "if cached, return; otherwise create, cache and return" pattern.
     *
     * <p>If the specified key is not already associated with a value, attempts to compute its
     * value asynchronously using the given mapping function and enters it into this cache unless
     * {@code null}. The entire method invocation is performed atomically, so the function is
     * applied at most once per key.
     *
     * <p><b>Warning:</b> {@code mappingFunction} <b>must not</b> attempt to update any other
     * mappings of this cache.
     *
     * @param key the key whose associated value is to be returned
     * @param mappingFunction the function to asynchronously compute a value
     * @return a CompletableFuture containing the current (existing or computed) value associated
     *     with the specified key, or {@code null} if the computed value is null
     * @throws NullPointerException if the specified key or mappingFunction is null
     */
    CompletableFuture<V> get(K key, Function<? super K, ? extends CompletableFuture<V>> mappingFunction);

    /**
     * Returns the value associated with {@code key} in this cache, or {@code null} if there is
     * no cached value for {@code key}.
     *
     * @param key the key whose associated value is to be returned
     * @return a CompletableFuture containing the value to which the specified key is mapped, or
     *     {@code null} if this cache contains no mapping for the key
     * @throws NullPointerException if the specified key is null
     */
    CompletableFuture<V> getIfPresent(K key);

    /**
     * Associates {@code value} with {@code key} in this cache. If the cache previously contained
     * a value associated with {@code key}, the old value is replaced by {@code value}.
     *
     * <p>Prefer {@link #get(Object, Function)} when using the conventional "if cached, return;
     * otherwise create, cache and return" pattern.
     *
     * @param key the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     * @return a CompletableFuture that completes when the put operation is done
     * @throws NullPointerException if the specified key or value is null
     */
    CompletableFuture<Void> put(K key, V value);

    /**
     * Returns a CompletableFuture containing a map of the values associated with {@code keys} in
     * this cache. The returned map will only contain entries which are already present in the
     * cache.
     *
     * @param keys the keys whose associated values are to be returned
     * @return a CompletableFuture containing an unmodifiable mapping of keys to values for keys
     *     that were found in the cache
     * @throws NullPointerException if the specified collection is null or contains a null element
     */
    CompletableFuture<Map<K, V>> getAllPresent(Iterable<? extends K> keys);

    /**
     * Copies all of the mappings from the specified map to the cache. The effect of this call is
     * equivalent to that of calling {@code put(k, v)} on this cache once for each mapping from
     * key {@code k} to value {@code v} in the specified map.
     *
     * @param map the mappings to be stored in this cache
     * @return a CompletableFuture that completes when all put operations are done
     * @throws NullPointerException if the specified map is null or contains a null key or value
     */
    CompletableFuture<Void> putAll(Map<? extends K, ? extends V> map);

    /**
     * Discards any cached value for key {@code key}.
     *
     * @param key the key whose mapping is to be removed from the cache
     * @return a CompletableFuture that completes when the invalidation is done
     * @throws NullPointerException if the specified key is null
     */
    CompletableFuture<Void> invalidate(K key);

    /**
     * Discards any cached values for keys {@code keys}.
     *
     * @param keys the keys whose mappings are to be removed from the cache
     * @return a CompletableFuture that completes when all invalidations are done
     * @throws NullPointerException if the specified collection is null or contains a null element
     */
    CompletableFuture<Void> invalidateAll(Iterable<? extends K> keys);

    /**
     * Discards all entries in the cache.
     *
     * @return a CompletableFuture that completes when the invalidation is done
     */
    CompletableFuture<Void> invalidateAll();

    /**
     * Returns the approximate number of entries in this cache. The value returned is an estimate;
     * the actual count may differ if there are concurrent insertions or removals.
     *
     * @return the estimated number of mappings
     */
    long estimatedSize();

    /**
     * Returns a current snapshot of this cache's cumulative statistics. All statistics are
     * initialized to zero, and are monotonically increasing over the lifetime of the cache.
     *
     * @return the current snapshot of the statistics of this cache
     */
    CacheStats stats();

    /**
     * Returns a view of the entries stored in this cache as a thread-safe map. Modifications made
     * to the map directly affect the cache.
     *
     * @return a thread-safe view of this cache
     */
    ConcurrentMap<K, V> asMap();

    /**
     * Returns the underlying synchronous cache that backs this async cache.
     *
     * @return the synchronous cache
     */
    Cache<K, V> synchronous();

    /**
     * Performs any pending maintenance operations needed by the cache. Exactly which activities
     * are performed -- if any -- is implementation-dependent.
     *
     * @return a CompletableFuture that completes when cleanup is done
     */
    default CompletableFuture<Void> cleanUp() {
        synchronous().cleanUp();
        return CompletableFuture.completedFuture(null);
    }
}
