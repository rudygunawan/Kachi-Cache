package com.github.rudygunawan.kachi.builder;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.CacheLoader;
import com.github.rudygunawan.kachi.api.Expiry;
import com.github.rudygunawan.kachi.api.LoadingCache;
import com.github.rudygunawan.kachi.api.RefreshPolicy;
import com.github.rudygunawan.kachi.api.Weigher;
import com.github.rudygunawan.kachi.impl.ConcurrentCacheImpl;
import com.github.rudygunawan.kachi.listener.RemovalListener;
import com.github.rudygunawan.kachi.policy.EvictionPolicy;

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
    private long maximumWeight = UNSET_INT;
    private Weigher<? super K, ? super V> weigher;
    private long expireAfterWriteNanos = UNSET_INT;
    private long expireAfterAccessNanos = UNSET_INT;
    private boolean recordStats = false;
    private EvictionPolicy evictionPolicy = EvictionPolicy.LRU;
    private RemovalListener<? super K, ? super V> removalListener;
    private Expiry<? super K, ? super V> expiry;
    private RefreshPolicy<? super K, ? super V> refreshPolicy;
    private long refreshAfterWriteNanos = UNSET_INT;

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
     * Specifies the maximum weight of entries the cache may contain. Weight is determined using the
     * {@link Weigher} specified in {@link #weigher}, and use of this method requires a corresponding
     * call to {@link #weigher} prior to calling {@link #build}.
     *
     * <p>Note that the cache may evict an entry before this limit is exceeded. As the cache size
     * grows close to the maximum, the cache evicts entries that are less likely to be used again.
     * For example, the cache may evict an entry because it hasn't been used recently or very often.
     *
     * <p>When {@code weight} is zero, elements will be evicted immediately after being loaded into the
     * cache. This can be useful in testing, or to disable caching temporarily without a code change.
     *
     * <p>This option is not required; if not specified, the cache has no weight-based eviction.
     *
     * <p><b>Note:</b> You can use both {@code maximumSize} and {@code maximumWeight} together. The cache
     * will evict when either limit is reached.
     *
     * @param weight the maximum total weight of entries the cache may contain
     * @return this builder instance
     * @throws IllegalArgumentException if {@code weight} is negative
     * @throws IllegalStateException if a maximum weight was already set
     * @see #weigher(Weigher)
     */
    public CacheBuilder<K, V> maximumWeight(long weight) {
        if (weight < 0) {
            throw new IllegalArgumentException("maximum weight must not be negative");
        }
        if (this.maximumWeight != UNSET_INT) {
            throw new IllegalStateException("maximum weight was already set to " + this.maximumWeight);
        }
        this.maximumWeight = weight;
        return this;
    }

    /**
     * Specifies the weigher to use in determining the weight of entries. Entry weight is taken
     * into consideration by {@link #maximumWeight(long)} when determining which entries to evict.
     *
     * <p>The weigher is called on every cache write (put/load). Keep the weight calculation fast
     * and simple. Avoid I/O, complex calculations, or operations that could throw exceptions.
     *
     * <p><b>Important:</b> Instead of returning {@code this} as a {@code CacheBuilder} instance,
     * this method returns {@code CacheBuilder<K1, V1>}. From this point on, the cache builder is
     * assumed to be of the correct types, and methods like {@code build()} will return cache
     * instances with those types.
     *
     * <p><b>Example usage:</b>
     * <pre>{@code
     * // Weight by byte array length
     * Cache<String, byte[]> cache = CacheBuilder.newBuilder()
     *     .maximumWeight(10_000_000)  // 10 MB
     *     .weigher((key, value) -> value.length)
     *     .build();
     *
     * // Weight by string length
     * Cache<String, String> cache = CacheBuilder.newBuilder()
     *     .maximumWeight(1_000_000)
     *     .weigher((key, value) -> key.length() + value.length())
     *     .build();
     * }</pre>
     *
     * @param <K1> the key type of the weigher
     * @param <V1> the value type of the weigher
     * @param weigher the weigher to use in calculating the weight of cache entries
     * @return this builder instance, with type parameters adjusted to match the weigher
     * @throws IllegalArgumentException if {@code weigher} is null
     * @throws IllegalStateException if a weigher was already set
     * @see #maximumWeight(long)
     * @see Weigher
     */
    public <K1 extends K, V1 extends V> CacheBuilder<K1, V1> weigher(
            Weigher<? super K1, ? super V1> weigher) {
        if (weigher == null) {
            throw new NullPointerException("weigher cannot be null");
        }
        if (this.weigher != null) {
            throw new IllegalStateException("weigher was already set");
        }

        @SuppressWarnings("unchecked")
        CacheBuilder<K1, V1> me = (CacheBuilder<K1, V1>) this;
        me.weigher = weigher;
        return me;
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
     * Specifies a custom expiration policy that determines how long each entry should be retained
     * in the cache. This allows different entries to have different expiration times based on their
     * key, value, or other application-specific logic.
     *
     * <p>When a custom {@link Expiry} is specified, it takes precedence over {@link #expireAfterWrite}
     * and {@link #expireAfterAccess}. However, if both are specified, they will work together
     * (the entry expires when either condition is met).
     *
     * <p><b>Note:</b> This is an advanced feature. For simple fixed TTL, use {@link #expireAfterWrite}
     * or {@link #expireAfterAccess} instead.
     *
     * <p>Example usage:
     * <pre>{@code
     * Expiry<String, User> expiry = new Expiry<String, User>() {
     *   public long expireAfterCreate(String key, User user, long currentTime) {
     *     return user.isPremium()
     *         ? TimeUnit.HOURS.toNanos(2)
     *         : TimeUnit.MINUTES.toNanos(30);
     *   }
     *   // ... implement other methods
     * };
     *
     * Cache<String, User> cache = CacheBuilder.newBuilder()
     *     .expireAfter(expiry)
     *     .build();
     * }</pre>
     *
     * @param expiry the custom expiration policy
     * @return this builder instance
     */
    public <K1 extends K, V1 extends V> CacheBuilder<K1, V1> expireAfter(
            Expiry<? super K1, ? super V1> expiry) {
        if (expiry == null) {
            throw new NullPointerException("expiry cannot be null");
        }
        @SuppressWarnings("unchecked")
        CacheBuilder<K1, V1> me = (CacheBuilder<K1, V1>) this;
        me.expiry = expiry;
        return me;
    }

    /**
     * Specifies a custom refresh policy that determines when and how frequently entries should be
     * automatically reloaded in the background. This allows different entries to be refreshed at
     * different rates based on time of day, key, value, or other application-specific logic.
     *
     * <p><b>Important:</b> Refresh policies only work with {@link LoadingCache} instances created
     * via {@link #build(CacheLoader)}. Regular caches created via {@link #build()} will ignore
     * refresh policies.
     *
     * <p>Refresh is performed asynchronously in the background. The old value continues to be
     * served while the new value is being loaded. If the refresh fails, the old value is retained.
     *
     * <p><b>Note:</b> This is an advanced feature intended for use cases where entries have
     * predictable access patterns, such as stock market data that is more active during trading
     * hours. This feature must be explicitly activated and is NOT enabled by default.
     *
     * <p>Example usage:
     * <pre>{@code
     * RefreshPolicy<String, Data> policy = new TimeBasedRefreshPolicy<>(ZoneId.of("America/New_York"))
     *     .addActiveWindow(9, 30, 16, 0, 1, TimeUnit.MINUTES)  // Market hours: every minute
     *     .setDefaultInterval(10, TimeUnit.MINUTES);            // After hours: every 10 minutes
     *
     * LoadingCache<String, Data> cache = CacheBuilder.newBuilder()
     *     .refreshAfter(policy)
     *     .build(key -> loadData(key));
     * }</pre>
     *
     * @param policy the custom refresh policy
     * @return this builder instance
     */
    public <K1 extends K, V1 extends V> CacheBuilder<K1, V1> refreshAfter(
            RefreshPolicy<? super K1, ? super V1> policy) {
        if (policy == null) {
            throw new NullPointerException("refresh policy cannot be null");
        }
        @SuppressWarnings("unchecked")
        CacheBuilder<K1, V1> me = (CacheBuilder<K1, V1>) this;
        me.refreshPolicy = policy;
        return me;
    }

    /**
     * Specifies that entries should be automatically refreshed once a fixed duration has elapsed
     * after the entry's creation or the most recent replacement of its value. This is similar to
     * Caffeine's refreshAfterWrite feature.
     *
     * <p><b>Important:</b> This only works with {@link LoadingCache} instances created via
     * {@link #build(CacheLoader)}. Regular caches will ignore this setting.
     *
     * <p>Refresh is performed asynchronously in the background. The old value continues to be
     * served while the new value is being loaded. If the refresh fails, the old value is retained.
     *
     * <p>This is a simpler alternative to {@link #refreshAfter(RefreshPolicy)} for cases where
     * a fixed refresh interval is sufficient.
     *
     * @param duration the length of time after an entry is created or updated that it should be
     *                 automatically refreshed
     * @param unit the time unit for the duration
     * @return this builder instance
     * @throws IllegalArgumentException if duration is negative
     */
    public CacheBuilder<K, V> refreshAfterWrite(long duration, TimeUnit unit) {
        if (duration < 0) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        this.refreshAfterWriteNanos = unit.toNanos(duration);
        return this;
    }

    /**
     * Builds a cache which does not automatically load values when keys are requested.
     *
     * @return a cache having the requested features
     */
    public <K1 extends K, V1 extends V> Cache<K1, V1> build() {
        return new ConcurrentCacheImpl<K1, V1>(this);
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
        return new ConcurrentCacheImpl<K1, V1>(this, loader);
    }

    // Package-private getters for ConcurrentCacheImpl
    public int getInitialCapacity() {
        return initialCapacity;
    }

    public int getConcurrencyLevel() {
        return concurrencyLevel;
    }

    public long getMaximumSize() {
        return maximumSize;
    }

    public long getMaximumWeight() {
        return maximumWeight;
    }

    public Weigher<? super K, ? super V> getWeigher() {
        return weigher;
    }

    public long getExpireAfterWriteNanos() {
        return expireAfterWriteNanos;
    }

    public long getExpireAfterAccessNanos() {
        return expireAfterAccessNanos;
    }

    public boolean isRecordingStats() {
        return recordStats;
    }

    public EvictionPolicy getEvictionPolicy() {
        return evictionPolicy;
    }

    public RemovalListener<? super K, ? super V> getRemovalListener() {
        return removalListener;
    }

    public Expiry<? super K, ? super V> getExpiry() {
        return expiry;
    }

    public RefreshPolicy<? super K, ? super V> getRefreshPolicy() {
        return refreshPolicy;
    }

    public long getRefreshAfterWriteNanos() {
        return refreshAfterWriteNanos;
    }
}
