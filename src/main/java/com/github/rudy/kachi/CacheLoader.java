package com.github.rudy.kachi;

/**
 * Computes or retrieves values, based on a key, for use in populating a {@link LoadingCache}.
 *
 * <p>Most implementations will only need to implement {@link #load}. Other methods may be
 * overridden as desired.
 *
 * <p>Usage example:
 * <pre>{@code
 * CacheLoader<Key, Graph> loader = new CacheLoader<Key, Graph>() {
 *   public Graph load(Key key) throws Exception {
 *     return createExpensiveGraph(key);
 *   }
 * };
 * LoadingCache<Key, Graph> cache = CacheBuilder.newBuilder().build(loader);
 * }</pre>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public abstract class CacheLoader<K, V> {

    /**
     * Computes or retrieves the value corresponding to {@code key}.
     *
     * @param key the non-null key whose value should be loaded
     * @return the value associated with {@code key}; <b>must not be null</b>
     * @throws Exception if unable to load the result
     */
    public abstract V load(K key) throws Exception;

    /**
     * Computes or retrieves the values corresponding to {@code keys}. This method is called by
     * {@link LoadingCache#getAll}.
     *
     * <p>If the returned map doesn't contain all requested {@code keys} then the entries it does
     * contain will be cached, but {@code getAll} will throw an exception. If the returned map
     * contains extra keys not present in {@code keys} then all returned entries will be cached,
     * but only the entries for {@code keys} will be returned from {@code getAll}.
     *
     * <p>This method should be overridden when bulk retrieval is significantly more efficient than
     * many individual lookups. Note that {@link LoadingCache#getAll} will defer to individual calls
     * to {@link LoadingCache#get} if this method is not overridden.
     *
     * @param keys the unique, non-null keys whose values should be loaded
     * @return a map from each key in {@code keys} to the value associated with that key; <b>may not
     *         contain null values</b>
     * @throws Exception if unable to load the result
     */
    public java.util.Map<K, V> loadAll(Iterable<? extends K> keys) throws Exception {
        throw new UnsupportedOperationException("loadAll not implemented");
    }
}
