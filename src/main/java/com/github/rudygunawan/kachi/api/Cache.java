package com.github.rudygunawan.kachi.api;

import com.github.rudygunawan.kachi.model.CacheStats;
import com.github.rudygunawan.kachi.policy.Policy;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;

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
     * Returns a map of the values associated with {@code keys} that are present in the cache.
     * Unlike {@link LoadingCache#getAll}, this method does not attempt to load missing values.
     *
     * <p>This is an optimized bulk operation that retrieves multiple entries efficiently.
     *
     * @param keys the keys whose associated values are to be returned
     * @return an unmodifiable mapping of keys to values for the keys that are present in this cache
     */
    Map<K, V> getAllPresent(Iterable<? extends K> keys);

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
     * <p>This is an optimized bulk operation that inserts multiple entries efficiently.
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
     * Discards any cached values for the specified keys.
     *
     * <p>This is an optimized bulk operation that removes multiple entries efficiently.
     *
     * @param keys the keys whose mappings are to be removed from the cache
     */
    void invalidateAll(Iterable<? extends K> keys);

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
     * Returns the approximate number of entries in this cache.
     *
     * <p>This method is semantically equivalent to {@link #size()} and is provided for
     * compatibility with Caffeine's API. The name "estimated" reflects that for some cache
     * implementations, the size may be approximate rather than exact, especially in
     * highly concurrent scenarios or when entries are in the process of being evicted.
     *
     * <p><b>Note:</b> Expired entries may be included in the count, but will never be visible
     * to read or write operations. Entries with garbage-collected keys or values (when using
     * weak/soft references) may also be counted until the next cleanup operation.
     *
     * @return the estimated number of mappings in this cache
     */
    default long estimatedSize() {
        return size();
    }

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

    /**
     * Attempts to compute a mapping for the specified key and its current mapped value (or
     * {@code null} if there is no current mapping).
     *
     * <p>The entire method invocation is performed atomically. The supplied function is invoked
     * exactly once per invocation of this method if the key is present in the map. Some attempted
     * update operations on this cache by other threads may be blocked while computation is in
     * progress, so the computation should be short and simple, and must not attempt to update any
     * other mappings of this cache.
     *
     * <p><b>Example usage:</b>
     * <pre>{@code
     * // Increment a counter
     * cache.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
     *
     * // Remove if value meets condition
     * cache.compute(key, (k, v) -> (v != null && v.isExpired()) ? null : v);
     * }</pre>
     *
     * @param key the key with which the specified value is to be associated
     * @param remappingFunction the function to compute a value
     * @return the new value associated with the specified key, or {@code null} if none
     * @throws NullPointerException if the specified key is null or the remappingFunction is null
     * @throws RuntimeException if the remappingFunction throws an exception
     */
    V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction);

    /**
     * If the specified key is not already associated with a value, attempts to compute its value
     * using the given mapping function and enters it into this cache unless {@code null}.
     *
     * <p>The entire method invocation is performed atomically. The supplied function is invoked
     * at most once per invocation of this method. Some attempted update operations on this cache
     * by other threads may be blocked while computation is in progress, so the computation should
     * be short and simple, and must not attempt to update any other mappings of this cache.
     *
     * <p><b>Example usage:</b>
     * <pre>{@code
     * // Lazy initialization
     * User user = cache.computeIfAbsent(userId, id -> database.fetchUser(id));
     *
     * // Create default value if missing
     * List<String> list = cache.computeIfAbsent(key, k -> new ArrayList<>());
     * }</pre>
     *
     * @param key the key with which the computed value is to be associated
     * @param mappingFunction the function to compute a value
     * @return the current (existing or computed) value associated with the specified key, or
     *         {@code null} if the computed value is null
     * @throws NullPointerException if the specified key is null or the mappingFunction is null
     * @throws RuntimeException if the mappingFunction throws an exception
     */
    V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

    /**
     * If the value for the specified key is present, attempts to compute a new mapping given the
     * key and its current mapped value.
     *
     * <p>The entire method invocation is performed atomically. The supplied function is invoked
     * exactly once per invocation of this method if the key is present. Some attempted update
     * operations on this cache by other threads may be blocked while computation is in progress,
     * so the computation should be short and simple, and must not attempt to update any other
     * mappings of this cache.
     *
     * <p><b>Example usage:</b>
     * <pre>{@code
     * // Update if present
     * cache.computeIfPresent(key, (k, v) -> v.withUpdatedTimestamp());
     *
     * // Remove if condition met
     * cache.computeIfPresent(key, (k, v) -> v.isValid() ? v : null);
     * }</pre>
     *
     * @param key the key with which the specified value is associated
     * @param remappingFunction the function to compute a value
     * @return the new value associated with the specified key, or {@code null} if none
     * @throws NullPointerException if the specified key is null or the remappingFunction is null
     * @throws RuntimeException if the remappingFunction throws an exception
     */
    V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction);

    /**
     * If the specified key is not already associated with a value or is associated with null,
     * associates it with the given non-null value. Otherwise, replaces the associated value with
     * the results of the given remapping function, or removes if the result is {@code null}.
     *
     * <p>The entire method invocation is performed atomically. The supplied function is invoked
     * at most once per invocation of this method if the key is present. Some attempted update
     * operations on this cache by other threads may be blocked while computation is in progress,
     * so the computation should be short and simple, and must not attempt to update any other
     * mappings of this cache.
     *
     * <p><b>Example usage:</b>
     * <pre>{@code
     * // Merge values (e.g., sum counters)
     * cache.merge(key, 1, Integer::sum);
     *
     * // Concatenate strings
     * cache.merge(key, "newValue", (old, new_) -> old + "," + new_);
     * }</pre>
     *
     * @param key the key with which the resulting value is to be associated
     * @param value the non-null value to be merged with the existing value associated with the key
     *              or, if no existing value, to be associated with the key
     * @param remappingFunction the function to recompute a value if present
     * @return the new value associated with the specified key, or {@code null} if no value is
     *         associated with the key
     * @throws NullPointerException if the specified key is null, the value is null, or the
     *                              remappingFunction is null
     * @throws RuntimeException if the remappingFunction throws an exception
     */
    V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction);

    /**
     * Returns access to inspect and modify the cache's operational characteristics at runtime.
     *
     * <p>The policy provides introspection and modification capabilities for:
     * <ul>
     *   <li>Eviction settings (maximum size, eviction policy, current weight)</li>
     *   <li>Expiration settings (TTL after write/access, entry age)</li>
     * </ul>
     *
     * <p><b>Example usage:</b>
     * <pre>{@code
     * Cache<String, User> cache = CacheBuilder.newBuilder()
     *     .maximumSize(1000)
     *     .expireAfterWrite(10, TimeUnit.MINUTES)
     *     .build();
     *
     * Policy<String, User> policy = cache.policy();
     *
     * // Inspect current settings
     * policy.eviction().ifPresent(eviction -> {
     *     System.out.println("Max: " + eviction.getMaximum());
     *     System.out.println("Current: " + eviction.weightedSize());
     * });
     *
     * // Dynamically resize
     * policy.eviction().ifPresent(eviction -> eviction.setMaximum(2000));
     *
     * // Query entry age
     * policy.expiration().ifPresent(expiration -> {
     *     long age = expiration.ageOf("userId");
     *     System.out.println("Entry age: " + age + " ns");
     * });
     * }</pre>
     *
     * @return access to the cache's policy
     */
    Policy<K, V> policy();
}
