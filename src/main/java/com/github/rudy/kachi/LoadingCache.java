package com.github.rudy.kachi;

import java.util.Map;

/**
 * A semi-persistent mapping from keys to values. Values are automatically loaded by the cache,
 * and are stored in the cache until either evicted or manually invalidated.
 *
 * <p>Implementations of this interface are expected to be thread-safe, and can be safely accessed
 * by multiple concurrent threads.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 */
public interface LoadingCache<K, V> extends Cache<K, V> {

    /**
     * Returns the value associated with {@code key} in this cache, first loading that value if
     * necessary. No observable state associated with this cache is modified until loading completes.
     *
     * @param key the key whose associated value is to be returned
     * @return the current (existing or computed) value associated with the specified key
     * @throws Exception if unable to load a value for the key
     */
    V get(K key) throws Exception;

    /**
     * Returns a map of the values associated with {@code keys}, creating or retrieving those values
     * if necessary. The returned map contains entries that were already cached, combined with newly
     * loaded entries.
     *
     * @param keys the keys whose associated values are to be returned
     * @return an unmodifiable mapping of keys to values for the specified keys in this cache
     * @throws Exception if unable to load values for the keys
     */
    Map<K, V> getAll(Iterable<? extends K> keys) throws Exception;

    /**
     * Loads a new value for key {@code key}, possibly asynchronously. While the new value is loading
     * the previous value (if any) will continue to be returned by {@code get(key)} unless it is
     * evicted. If the new value is loaded successfully it will replace the previous value in the
     * cache; if an exception is thrown while refreshing the old value will remain, and the exception
     * will be logged and swallowed.
     *
     * @param key the key whose value should be refreshed
     */
    void refresh(K key);
}
