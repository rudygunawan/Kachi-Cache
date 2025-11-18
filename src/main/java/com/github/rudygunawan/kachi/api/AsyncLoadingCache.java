package com.github.rudygunawan.kachi.api;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A semi-persistent mapping from keys to values with asynchronous automatic loading. Values are
 * automatically loaded by the cache using an {@link AsyncCacheLoader}, and are stored in the cache
 * until evicted.
 *
 * <p>Implementations of this interface are expected to be thread-safe, and can be safely accessed
 * by multiple concurrent threads.
 *
 * <p><b>Example usage:</b>
 * <pre>{@code
 * AsyncLoadingCache<String, User> cache = CacheBuilder.newBuilder()
 *     .maximumSize(1000)
 *     .expireAfterWrite(10, TimeUnit.MINUTES)
 *     .buildAsync(key -> CompletableFuture.supplyAsync(() -> database.fetchUser(key)));
 *
 * // Non-blocking get with automatic loading
 * CompletableFuture<User> future = cache.get("userId");
 * future.thenAccept(user -> System.out.println("Got user: " + user));
 *
 * // Get multiple users in parallel
 * CompletableFuture<Map<String, User>> users = cache.getAll(List.of("id1", "id2", "id3"));
 * }</pre>
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 * @since 1.0.0
 */
public interface AsyncLoadingCache<K, V> extends AsyncCache<K, V> {

    /**
     * Returns the value associated with {@code key} in this cache, obtaining that value from
     * {@link AsyncCacheLoader#asyncLoad} if necessary. This method provides a simple substitute
     * for the conventional "if cached, return; otherwise create, cache and return" pattern.
     *
     * <p><b>Warning:</b> loading <b>must not</b> attempt to update any other mappings of this
     * cache.
     *
     * @param key the key whose associated value is to be returned
     * @return a CompletableFuture containing the current (existing or computed) value associated
     *     with the specified key
     * @throws NullPointerException if the specified key is null
     */
    CompletableFuture<V> get(K key);

    /**
     * Returns a CompletableFuture containing a map of the values associated with {@code keys},
     * creating or retrieving those values if necessary. The returned map contains entries that
     * were already cached, combined with newly loaded entries.
     *
     * <p>Caches loaded by a {@link AsyncCacheLoader} will issue a single request to
     * {@link AsyncCacheLoader#asyncLoadAll} for all keys which are not already present in the
     * cache. All entries returned by {@link AsyncCacheLoader#asyncLoadAll} will be stored in the
     * cache, over-writing any previously cached values.
     *
     * <p>Note that duplicate elements in {@code keys}, as determined by {@link Object#equals},
     * will be ignored.
     *
     * @param keys the keys whose associated values are to be returned
     * @return a CompletableFuture containing an unmodifiable mapping of keys to values
     * @throws NullPointerException if the specified collection is null or contains a null element
     */
    CompletableFuture<Map<K, V>> getAll(Iterable<? extends K> keys);

    /**
     * Loads a new value for {@code key}, asynchronously. While the new value is loading the
     * previous value (if any) will continue to be returned by {@code get(key)} unless it is
     * evicted. If the new value is loaded successfully it will replace the previous value in the
     * cache; if an exception is thrown while refreshing the old value will remain, <i>and the
     * exception will be logged and swallowed</i>.
     *
     * <p><b>Note:</b> <i>all exceptions thrown during refresh will be logged and then swallowed</i>.
     *
     * @param key the key whose value should be refreshed
     * @return a CompletableFuture that completes when the refresh is done
     * @throws NullPointerException if the specified key is null
     */
    CompletableFuture<Void> refresh(K key);

    /**
     * Returns the underlying synchronous loading cache that backs this async loading cache.
     *
     * @return the synchronous loading cache
     */
    @Override
    LoadingCache<K, V> synchronous();
}
