package com.github.rudy.kachi;

import java.util.concurrent.TimeUnit;

/**
 * A builder of {@link Cache} and {@link LoadingCache} instances having any combination of the
 * following features:
 *
 * <ul>
 *   <li>automatic loading of entries into the cache
 *   <li>time-based expiration of entries, measured since last access or last write
 *   <li>size-based eviction when a maximum size is exceeded
 *   <li>notification of evicted entries
 *   <li>accumulation of cache access statistics
 * </ul>
 *
 * <p>These features are all optional; caches can be created using all or none of them. By default
 * cache instances created by {@code CacheBuilder} will not perform any type of eviction.
 *
 * <p>Usage example:
 * <pre>{@code
 * LoadingCache<Key, Graph> graphs = CacheBuilder.newBuilder()
 *     .maximumSize(10000)
 *     .expireAfterWrite(10, TimeUnit.MINUTES)
 *     .recordStats()
 *     .build(
 *         new CacheLoader<Key, Graph>() {
 *           public Graph load(Key key) throws Exception {
 *             return createExpensiveGraph(key);
 *           }
 *         });
 * }</pre>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public class CacheBuilder<K, V> {
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final int DEFAULT_CONCURRENCY_LEVEL = 4;
    private static final long UNSET_INT = -1;

    private int initialCapacity = DEFAULT_INITIAL_CAPACITY;
    private int concurrencyLevel = DEFAULT_CONCURRENCY_LEVEL;
    private long maximumSize = UNSET_INT;
    private long expireAfterWriteNanos = UNSET_INT;
    private long expireAfterAccessNanos = UNSET_INT;
    private boolean recordStats = false;
    private EvictionPolicy evictionPolicy = EvictionPolicy.LRU;
    private RemovalListener<? super K, ? super V> removalListener;

    private CacheBuilder() {
    }

    /**
     * Constructs a new {@code CacheBuilder} instance with default settings.
     */
    public static CacheBuilder<Object, Object> newBuilder() {
        return new CacheBuilder<>();
    }

    /**
     * Sets the minimum total size for the internal hash tables. For example, if the initial
     * capacity is {@code 60}, and the concurrency level is {@code 8}, then eight segments are
     * created, each having a hash table of size eight.
     *
     * <p>This option is not required; by default the initial capacity is 16.
     *
     * @param initialCapacity the initial capacity
     * @return this builder instance
     * @throws IllegalArgumentException if {@code initialCapacity} is negative
     */
    public CacheBuilder<K, V> initialCapacity(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initial capacity must not be negative");
        }
        this.initialCapacity = initialCapacity;
        return this;
    }

    /**
     * Guides the allowed concurrency among update operations. Used as a hint for internal sizing.
     *
     * <p>This option is not required; by default the concurrency level is 4.
     *
     * @param concurrencyLevel the concurrency level
     * @return this builder instance
     * @throws IllegalArgumentException if {@code concurrencyLevel} is not positive
     */
    public CacheBuilder<K, V> concurrencyLevel(int concurrencyLevel) {
        if (concurrencyLevel <= 0) {
            throw new IllegalArgumentException("concurrency level must be positive");
        }
        this.concurrencyLevel = concurrencyLevel;
        return this;
    }

    /**
     * Specifies the maximum number of entries the cache may contain. When the cache reaches this
     * size, cache evictions will occur. Eviction is based on the least recently accessed entries.
     *
     * <p>When {@code size} is zero, elements will be evicted immediately after being loaded into the
     * cache. This can be useful in testing, or to disable caching temporarily without a code change.
     *
     * <p>This option is not required; if not specified, the cache has no size-based eviction.
     *
     * @param size the maximum size of the cache
     * @return this builder instance
     * @throws IllegalArgumentException if {@code size} is negative
     */
    public CacheBuilder<K, V> maximumSize(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("maximum size must not be negative");
        }
        this.maximumSize = size;
        return this;
    }

    /**
     * Specifies that each entry should be automatically removed from the cache once a fixed duration
     * has elapsed after the entry's creation, or the most recent replacement of its value.
     *
     * <p>When {@code duration} is zero, elements will be evicted immediately after being loaded into
     * the cache. This can be useful in testing, or to disable caching temporarily without a code
     * change.
     *
     * <p>Expired entries may be counted in {@link Cache#size}, but will never be visible to read or
     * write operations.
     *
     * @param duration the length of time after an entry is created that it should be automatically
     *     removed
     * @param unit the unit that {@code duration} is expressed in
     * @return this builder instance
     * @throws IllegalArgumentException if {@code duration} is negative
     */
    public CacheBuilder<K, V> expireAfterWrite(long duration, TimeUnit unit) {
        if (duration < 0) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        this.expireAfterWriteNanos = unit.toNanos(duration);
        return this;
    }

    /**
     * Specifies that each entry should be automatically removed from the cache once a fixed duration
     * has elapsed after the entry's creation, the most recent replacement of its value, or its last
     * access.
     *
     * <p>When {@code duration} is zero, elements will be evicted immediately after being loaded into
     * the cache. This can be useful in testing, or to disable caching temporarily without a code
     * change.
     *
     * <p>Expired entries may be counted in {@link Cache#size}, but will never be visible to read or
     * write operations.
     *
     * @param duration the length of time after an entry is last accessed that it should be
     *     automatically removed
     * @param unit the unit that {@code duration} is expressed in
     * @return this builder instance
     * @throws IllegalArgumentException if {@code duration} is negative
     */
    public CacheBuilder<K, V> expireAfterAccess(long duration, TimeUnit unit) {
        if (duration < 0) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        this.expireAfterAccessNanos = unit.toNanos(duration);
        return this;
    }

    /**
     * Enables the accumulation of {@link CacheStats} during the operation of the cache. Without this
     * {@link Cache#stats} will return zero for all statistics.
     *
     * @return this builder instance
     */
    public CacheBuilder<K, V> recordStats() {
        this.recordStats = true;
        return this;
    }

    /**
     * Specifies the eviction policy to use when the cache reaches its maximum size.
     *
     * <p>Available policies:
     * <ul>
     *   <li>{@link EvictionPolicy#LRU} - Least Recently Used (default)
     *   <li>{@link EvictionPolicy#LFU} - Least Frequently Used
     *   <li>{@link EvictionPolicy#FIFO} - First In First Out
     * </ul>
     *
     * @param policy the eviction policy to use
     * @return this builder instance
     */
    public CacheBuilder<K, V> evictionPolicy(EvictionPolicy policy) {
        if (policy == null) {
            throw new NullPointerException("eviction policy cannot be null");
        }
        this.evictionPolicy = policy;
        return this;
    }

    /**
     * Specifies a listener instance that caches should notify each time an entry is removed for any
     * reason. Each cache created by this builder will invoke this listener as part of the routine
     * maintenance described in the {@link Cache} documentation.
     *
     * <p><b>Warning:</b> all exceptions thrown by {@code listener} will be logged and then swallowed.
     *
     * @param listener the removal listener to use
     * @return this builder instance
     */
    public <K1 extends K, V1 extends V> CacheBuilder<K1, V1> removalListener(
            RemovalListener<? super K1, ? super V1> listener) {
        if (listener == null) {
            throw new NullPointerException("removal listener cannot be null");
        }
        @SuppressWarnings("unchecked")
        CacheBuilder<K1, V1> me = (CacheBuilder<K1, V1>) this;
        me.removalListener = listener;
        return me;
    }

    /**
     * Builds a cache which does not automatically load values when keys are requested.
     *
     * @return a cache having the requested features
     */
    public <K1 extends K, V1 extends V> Cache<K1, V1> build() {
        return new ConcurrentCacheImpl<>(this);
    }

    /**
     * Builds a cache, which either returns an already-loaded value for a given key or atomically
     * computes or retrieves it using the supplied {@code CacheLoader}. If another thread is currently
     * loading the value for this key, simply waits for that thread to finish and returns its loaded
     * value.
     *
     * @param loader the cache loader used to obtain new values
     * @return a cache having the requested features
     */
    public <K1 extends K, V1 extends V> LoadingCache<K1, V1> build(CacheLoader<? super K1, V1> loader) {
        return new ConcurrentCacheImpl<>(this, loader);
    }

    // Package-private getters for ConcurrentCacheImpl
    int getInitialCapacity() {
        return initialCapacity;
    }

    int getConcurrencyLevel() {
        return concurrencyLevel;
    }

    long getMaximumSize() {
        return maximumSize;
    }

    long getExpireAfterWriteNanos() {
        return expireAfterWriteNanos;
    }

    long getExpireAfterAccessNanos() {
        return expireAfterAccessNanos;
    }

    boolean isRecordingStats() {
        return recordStats;
    }

    EvictionPolicy getEvictionPolicy() {
        return evictionPolicy;
    }

    RemovalListener<? super K, ? super V> getRemovalListener() {
        return removalListener;
    }
}
