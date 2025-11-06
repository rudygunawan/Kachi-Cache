package com.github.rudygunawan.kachi.api;

import com.github.rudygunawan.kachi.model.CacheStats;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A semi-persistent mapping from keys to values. Cache entries are manually added using
 * {@link #put(Object, Object)}, and are stored in the cache until either evicted or manually
 * invalidated.
 *
 * <p>Implementations of this interface are expected to be thread-safe, and can be safely accessed
 * by multiple concurrent threads.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 */
public interface Cache<K, V> {

    /**
     * Returns the value associated with {@code key} in this cache, or {@code null} if there is no
     * cached value for {@code key}.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or {@code null} if this cache
     *         contains no mapping for the key
     */
    V getIfPresent(K key);

    /**
     * Returns the value associated with {@code key} in this cache, obtaining that value from
     * {@code loader} if necessary. This method provides a simple substitute for the conventional
     * "if cached, return; otherwise create, cache and return" pattern.
     *
     * @param key the key whose associated value is to be returned
     * @param loader the function to compute a value
     * @return the current (existing or computed) value associated with the specified key
     * @throws Exception if the loader throws an exception
     */
    V get(K key, Callable<? extends V> loader) throws Exception;

    /**
     * Associates {@code value} with {@code key} in this cache. If the cache previously contained a
     * value associated with {@code key}, the old value is replaced by {@code value}.
     *
     * @param key the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     */
    void put(K key, V value);

    /**
     * Copies all of the mappings from the specified map to the cache.
     *
     * @param map mappings to be stored in this cache
     */
    void putAll(Map<? extends K, ? extends V> map);

    /**
     * Discards any cached value for key {@code key}.
     *
     * @param key the key whose mapping is to be removed from the cache
     */
    void invalidate(K key);

    /**
     * Discards all entries in the cache.
     */
    void invalidateAll();

    /**
     * Returns the approximate number of entries in this cache.
     *
     * @return the number of key-value mappings in this cache
     */
    long size();

    /**
     * Returns a current snapshot of this cache's cumulative statistics. All statistics are
     * initialized to zero, and are monotonically increasing over the lifetime of the cache.
     *
     * @return the cache statistics
     */
    CacheStats stats();

    /**
     * Performs any pending maintenance operations needed by the cache. Exactly which activities are
     * performed -- if any -- is implementation-dependent.
     */
    void cleanUp();

    /**
     * Returns a view of the entries stored in this cache as a thread-safe map.
     *
     * @return a concurrent map view of this cache
     */
    Map<K, V> asMap();
}
