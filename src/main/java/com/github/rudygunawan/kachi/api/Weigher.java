package com.github.rudygunawan.kachi.api;

/**
 * Calculates the weight of cache entries for size-based eviction.
 *
 * <p>A weigher is used when cache entries have variable sizes and you want to limit
 * the cache by total weight rather than entry count. For example, you might want to
 * limit a cache to 10 MB of memory rather than 1000 entries.
 *
 * <p>Weight must be non-negative and should be relatively stable for a given entry.
 * The cache uses weights to determine when to evict entries based on the configured
 * {@code maximumWeight}.
 *
 * <p><b>Important:</b> The weigher is called on every cache write (put/load). Keep
 * the weight calculation fast and simple. Avoid I/O, complex calculations, or
 * operations that could throw exceptions.
 *
 * <h3>Example Use Cases</h3>
 *
 * <p><b>1. Memory-based caching (images, documents):</b>
 * <pre>{@code
 * Weigher<String, byte[]> byteArrayWeigher = (key, value) -> value.length;
 *
 * Cache<String, byte[]> cache = CacheBuilder.newBuilder()
 *     .maximumWeight(10_000_000)  // 10 MB limit
 *     .weigher(byteArrayWeigher)
 *     .build();
 * }</pre>
 *
 * <p><b>2. String length-based caching:</b>
 * <pre>{@code
 * Weigher<String, String> stringWeigher = (key, value) ->
 *     key.length() + value.length();
 *
 * Cache<String, String> cache = CacheBuilder.newBuilder()
 *     .maximumWeight(1_000_000)  // 1M characters
 *     .weigher(stringWeigher)
 *     .build();
 * }</pre>
 *
 * <p><b>3. Object size estimation:</b>
 * <pre>{@code
 * Weigher<String, User> userWeigher = (key, value) -> {
 *     // Rough estimation: object overhead + string fields
 *     return 100 + key.length() +
 *            value.getName().length() +
 *            value.getEmail().length();
 * };
 *
 * Cache<String, User> cache = CacheBuilder.newBuilder()
 *     .maximumWeight(10_000)  // ~10K estimated bytes
 *     .weigher(userWeigher)
 *     .build();
 * }</pre>
 *
 * <h3>Design Considerations</h3>
 *
 * <p><b>Performance:</b> The weigher is called on every write, so keep it fast:
 * <ul>
 *   <li>✅ Good: {@code value.length()}</li>
 *   <li>✅ Good: {@code value.size()}</li>
 *   <li>✅ Good: Simple arithmetic on cached fields</li>
 *   <li>❌ Bad: Deep object traversal</li>
 *   <li>❌ Bad: I/O operations</li>
 *   <li>❌ Bad: Complex calculations</li>
 * </ul>
 *
 * <p><b>Accuracy vs Performance:</b> Weights don't need to be exact. An
 * approximation is fine as long as it's proportional to actual size.
 *
 * <p><b>Weight Stability:</b> The weight of an entry should not change after
 * it's been cached. If your objects are mutable and their weight changes,
 * consider:
 * <ul>
 *   <li>Making defensive copies on cache write</li>
 *   <li>Using immutable objects</li>
 *   <li>Calculating weight based on stable properties only</li>
 * </ul>
 *
 * <p><b>Combining with Entry Count:</b> You can use both {@code maximumSize}
 * (entry count) and {@code maximumWeight} together. The cache will evict when
 * either limit is reached. However, in most cases, you'll use one or the other.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 *
 * @see com.github.rudygunawan.kachi.builder.CacheBuilder#maximumWeight(long)
 * @see com.github.rudygunawan.kachi.builder.CacheBuilder#weigher(Weigher)
 */
@FunctionalInterface
public interface Weigher<K, V> {

    /**
     * Returns the weight of a cache entry.
     *
     * <p>The weight must be non-negative. A weight of zero is valid and means
     * the entry consumes no weight quota (though it still counts toward entry count).
     *
     * <p><b>Implementation notes:</b>
     * <ul>
     *   <li>Must not return negative weights (will throw IllegalStateException)</li>
     *   <li>Should be deterministic (same inputs = same output)</li>
     *   <li>Should be fast (called on every cache write)</li>
     *   <li>Should not throw exceptions (will cause cache operation to fail)</li>
     *   <li>Weight should be stable (not change after caching)</li>
     * </ul>
     *
     * @param key the cache key (never null)
     * @param value the cache value (never null)
     * @return the weight of the entry, must be non-negative
     * @throws IllegalStateException if the weight is negative (will be caught by cache)
     */
    int weigh(K key, V value);

    /**
     * Returns a weigher that always returns 1 for any entry.
     * This is equivalent to counting entries (same as maximumSize).
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return a weigher that returns 1 for all entries
     */
    static <K, V> Weigher<K, V> singletonWeigher() {
        return (key, value) -> 1;
    }

    /**
     * Returns a weigher that weighs entries by the byte array value length.
     * Useful for caching binary data like images or documents.
     *
     * @param <K> the type of keys
     * @return a weigher for byte arrays
     */
    static <K> Weigher<K, byte[]> byteArrayWeigher() {
        return (key, value) -> value.length;
    }

    /**
     * Returns a weigher that weighs entries by string length (key + value).
     * Useful for caching text data.
     *
     * @return a weigher for strings
     */
    static Weigher<String, String> stringWeigher() {
        return (key, value) -> key.length() + value.length();
    }

    /**
     * Returns a weigher that weighs entries by value string length only.
     * Useful when keys are small compared to values.
     *
     * @param <K> the type of keys
     * @return a weigher for string values
     */
    static <K> Weigher<K, String> valueStringWeigher() {
        return (key, value) -> value.length();
    }
}
