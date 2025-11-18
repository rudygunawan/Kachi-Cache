package com.github.rudygunawan.kachi.api;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Computes or retrieves values asynchronously, based on a key, for use in populating an
 * {@link AsyncLoadingCache}.
 *
 * <p>Most implementations will only need to implement {@link #asyncLoad}. Other methods
 * may be overridden as desired.
 *
 * <p><b>Example usage:</b>
 * <pre>{@code
 * AsyncCacheLoader<String, User> loader = new AsyncCacheLoader<String, User>() {
 *   @Override
 *   public CompletableFuture<User> asyncLoad(String key, Executor executor) {
 *     return CompletableFuture.supplyAsync(() -> {
 *       return database.fetchUser(key);
 *     }, executor);
 *   }
 * };
 *
 * AsyncLoadingCache<String, User> cache = CacheBuilder.newBuilder()
 *     .maximumSize(1000)
 *     .buildAsync(loader);
 * }</pre>
 *
 * <p><b>Example with external async API:</b>
 * <pre>{@code
 * AsyncCacheLoader<String, Data> loader = (key, executor) -> {
 *   // Use existing async API
 *   return httpClient.getAsync(url + key)
 *       .thenApplyAsync(response -> parseData(response), executor);
 * };
 * }</pre>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @since 1.0.0
 */
@FunctionalInterface
public interface AsyncCacheLoader<K, V> {

    /**
     * Asynchronously computes or retrieves the value corresponding to {@code key}.
     *
     * <p><b>Warning:</b> Loading must not attempt to update any other mappings of this cache
     * directly.
     *
     * @param key the non-null key whose value should be loaded
     * @param executor the executor to use for async operations
     * @return a CompletableFuture that completes with the loaded value
     * @throws Exception if unable to load the result
     * @throws InterruptedException if this method is interrupted
     */
    CompletableFuture<V> asyncLoad(K key, Executor executor) throws Exception;

    /**
     * Asynchronously computes or retrieves the values corresponding to {@code keys}. This method
     * is called by {@link AsyncLoadingCache#getAll}.
     *
     * <p>If the returned map doesn't contain all requested {@code keys}, then the entries it does
     * contain will be cached and {@code getAll} will return the partial results. If the returned
     * map contains extra keys not present in {@code keys}, then all returned entries will be
     * cached, but only the entries for {@code keys} will be returned from {@code getAll}.
     *
     * <p>This method should be overridden when bulk retrieval is significantly more efficient than
     * many individual lookups. Note that {@link AsyncLoadingCache#getAll} will defer to individual
     * calls to {@link AsyncLoadingCache#get} if this method is not overridden.
     *
     * @param keys the unique, non-null keys whose values should be loaded
     * @param executor the executor to use for async operations
     * @return a CompletableFuture that completes with a map from each key in {@code keys} to the
     *     value associated with that key; <b>may not contain null values</b>
     * @throws Exception if unable to load the result
     * @throws InterruptedException if this method is interrupted
     */
    default CompletableFuture<Map<K, V>> asyncLoadAll(
            Iterable<? extends K> keys, Executor executor) throws Exception {
        throw new UnsupportedOperationException();
    }

    /**
     * Asynchronously computes or retrieves a replacement value corresponding to an already-cached
     * {@code key}. If the replacement value is not found then the mapping will be removed if
     * {@code null} is returned. This method is called when an existing cache entry is refreshed
     * by {@link AsyncLoadingCache#refresh}, or through a call to {@link AsyncLoadingCache#get}
     * when {@code refreshAfterWrite} is configured.
     *
     * <p><b>Note:</b> <i>all exceptions thrown by this method will be logged and then swallowed</i>.
     *
     * @param key the non-null key whose value should be loaded
     * @param oldValue the non-null old value corresponding to {@code key}
     * @param executor the executor to use for async operations
     * @return a CompletableFuture that completes with the new value associated with {@code key},
     *     or {@code null} if the mapping is to be removed
     * @throws Exception if unable to reload the result
     * @throws InterruptedException if this method is interrupted
     */
    default CompletableFuture<V> asyncReload(K key, V oldValue, Executor executor) throws Exception {
        return asyncLoad(key, executor);
    }
}
