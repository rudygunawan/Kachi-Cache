package com.github.rudygunawan.kachi.builder;

import com.github.rudygunawan.kachi.api.AsyncCache;
import com.github.rudygunawan.kachi.api.AsyncCacheLoader;
import com.github.rudygunawan.kachi.api.AsyncLoadingCache;
import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.CacheLoader;
import com.github.rudygunawan.kachi.api.CacheStrategy;
import com.github.rudygunawan.kachi.api.Expiry;
import com.github.rudygunawan.kachi.api.LoadingCache;
import com.github.rudygunawan.kachi.api.RefreshPolicy;
import com.github.rudygunawan.kachi.api.Weigher;
import com.github.rudygunawan.kachi.impl.AsyncCacheImpl;
import com.github.rudygunawan.kachi.impl.AsyncLoadingCacheImpl;
import com.github.rudygunawan.kachi.impl.ConcurrentCacheImpl;
import com.github.rudygunawan.kachi.impl.HighPerformanceCacheImpl;
import com.github.rudygunawan.kachi.impl.PrecisionCacheImpl;
import com.github.rudygunawan.kachi.listener.CacheWriter;
import com.github.rudygunawan.kachi.listener.EvictionListener;
import com.github.rudygunawan.kachi.listener.PutListener;
import com.github.rudygunawan.kachi.listener.RemovalListener;
import com.github.rudygunawan.kachi.policy.EvictionPolicy;
import com.github.rudygunawan.kachi.policy.Strength;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
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
    private EvictionListener<? super K, ? super V> evictionListener;
    private PutListener<? super K, ? super V> putListener;
    private CacheWriter<? super K, ? super V> cacheWriter;
    private Expiry<? super K, ? super V> expiry;
    private RefreshPolicy<? super K, ? super V> refreshPolicy;
    private long refreshAfterWriteNanos = UNSET_INT;
    private CacheStrategy strategy = CacheStrategy.HIGH_PERFORMANCE; // Default to fast
    private Executor executor;
    private ScheduledExecutorService scheduler;
    private Strength keyStrength = Strength.STRONG;
    private Strength valueStrength = Strength.STRONG;

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
     * Specifies the cache implementation strategy.
     *
     * <p><b>HIGH_PERFORMANCE</b> (default):
     * <ul>
     *   <li>GET: ~63ns (15.88M ops/sec)</li>
     *   <li>Concurrent: 17.2M ops/sec - 5-8x faster than Caffeine!</li>
     *   <li>Lock-free reads, random eviction</li>
     *   <li>Best for: high-frequency reads, concurrent workloads</li>
     * </ul>
     *
     * <p><b>PRECISION</b>:
     * <ul>
     *   <li>GET: ~800-1,400ns (still respectable)</li>
     *   <li>Accurate LRU/FIFO/LFU/TinyLFU eviction</li>
     *   <li>Per-key locking, immediate expiry checking</li>
     *   <li>Best for: memory-constrained apps, need accurate eviction</li>
     * </ul>
     *
     * <p><b>Example - Switch with ONE line:</b>
     * <pre>{@code
     * // Fast (default)
     * var cache = CacheBuilder.newBuilder()
     *     .strategy(CacheStrategy.HIGH_PERFORMANCE)
     *     .maximumSize(10000)
     *     .build();
     *
     * // Accurate
     * var cache = CacheBuilder.newBuilder()
     *     .strategy(CacheStrategy.PRECISION)
     *     .maximumSize(10000)
     *     .build();
     * }</pre>
     *
     * @param strategy the cache implementation strategy
     * @return this builder instance
     */
    public CacheBuilder<K, V> strategy(CacheStrategy strategy) {
        if (strategy == null) {
            throw new NullPointerException("cache strategy cannot be null");
        }
        this.strategy = strategy;
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
     * Specifies a listener instance that caches should notify each time an entry is put into the
     * cache. The listener is invoked synchronously during the put operation and can distinguish
     * between new insertions (INSERT) and updates to existing entries (UPDATE).
     *
     * <p>This is particularly useful for async database upsert operations, cache write-through
     * patterns, audit logging, and event streaming.
     *
     * <p><b>Performance Considerations:</b>
     * <ul>
     *   <li>The listener is invoked synchronously, so it affects put() latency</li>
     *   <li>For long-running operations like database writes, use async processing:
     *       <pre>{@code
     *       .putListener((key, value, cause) -> {
     *           if (cause.isNewEntry()) {
     *               executor.submit(() -> database.insert(key, value));
     *           }
     *       })
     *       }</pre>
     *   </li>
     * </ul>
     *
     * <p><b>Warning:</b> all exceptions thrown by {@code listener} will be logged and then swallowed
     * to prevent the put operation from failing.
     *
     * <p><b>Example - Async Database Upsert:</b>
     * <pre>{@code
     * Cache<String, User> cache = CacheBuilder.newBuilder()
     *     .maximumSize(1000)
     *     .putListener((key, user, cause) -> {
     *         asyncExecutor.submit(() -> {
     *             if (cause == PutCause.INSERT) {
     *                 userRepository.insert(key, user);
     *             } else {
     *                 userRepository.update(key, user);
     *             }
     *         });
     *     })
     *     .build();
     * }</pre>
     *
     * @param listener the put listener to use
     * @return this builder instance
     * @see PutListener
     */
    public <K1 extends K, V1 extends V> CacheBuilder<K1, V1> putListener(
            PutListener<? super K1, ? super V1> listener) {
        if (listener == null) {
            throw new NullPointerException("put listener cannot be null");
        }
        @SuppressWarnings("unchecked")
        CacheBuilder<K1, V1> me = (CacheBuilder<K1, V1>) this;
        me.putListener = listener;
        return me;
    }

    /**
     * Specifies a writer instance for write-through or write-behind operations to external
     * storage (e.g., databases, file systems).
     *
     * <p>The {@link CacheWriter} provides structured write() and delete() methods that are
     * invoked synchronously during cache operations. This is more structured than
     * {@link PutListener} and includes deletion events.
     *
     * <p><b>Write-Through Example:</b>
     * <pre>{@code
     * Cache<String, User> cache = CacheBuilder.newBuilder()
     *     .maximumSize(1000)
     *     .writer(CacheWriter.sync(
     *         (key, user) -> database.upsert(key, user),
     *         (key, user, cause) -> {
     *           if (cause == RemovalCause.EXPLICIT) {
     *             database.delete(key);
     *           }
     *         }
     *     ))
     *     .build();
     * }</pre>
     *
     * <p><b>Async Write-Behind Example:</b>
     * <pre>{@code
     * ExecutorService writeExecutor = Executors.newFixedThreadPool(4);
     *
     * Cache<String, User> cache = CacheBuilder.newBuilder()
     *     .maximumSize(1000)
     *     .writer(CacheWriter.async(
     *         writeExecutor,
     *         (key, user) -> database.upsert(key, user),
     *         (key, user, cause) -> database.delete(key)
     *     ))
     *     .build();
     * }</pre>
     *
     * <p><b>Warning:</b> all exceptions thrown by {@code writer} will be logged and then swallowed.
     *
     * @param writer the cache writer to use
     * @return this builder instance
     * @see CacheWriter
     * @see PutListener
     */
    public <K1 extends K, V1 extends V> CacheBuilder<K1, V1> writer(
            CacheWriter<? super K1, ? super V1> writer) {
        if (writer == null) {
            throw new NullPointerException("cache writer cannot be null");
        }
        @SuppressWarnings("unchecked")
        CacheBuilder<K1, V1> me = (CacheBuilder<K1, V1>) this;
        me.cacheWriter = writer;
        return me;
    }

    /**
     * Specifies a custom executor for async cache operations and LoadingCache.
     *
     * <p>By default, Kachi uses virtual threads (JDK 21) for async operations. This method
     * allows you to provide a custom executor for:
     * <ul>
     *   <li>AsyncCache and AsyncLoadingCache operations</li>
     *   <li>LoadingCache background loading</li>
     *   <li>Async refresh operations</li>
     * </ul>
     *
     * <p><b>Example - Fixed Thread Pool:</b>
     * <pre>{@code
     * Executor customExecutor = Executors.newFixedThreadPool(10);
     *
     * AsyncCache<String, User> cache = CacheBuilder.newBuilder()
     *     .maximumSize(1000)
     *     .executor(customExecutor)
     *     .buildAsync();
     * }</pre>
     *
     * <p><b>Example - ForkJoinPool:</b>
     * <pre>{@code
     * AsyncCache<String, Data> cache = CacheBuilder.newBuilder()
     *     .executor(ForkJoinPool.commonPool())
     *     .buildAsync();
     * }</pre>
     *
     * @param executor the executor to use for async operations
     * @return this builder instance
     */
    public CacheBuilder<K, V> executor(Executor executor) {
        if (executor == null) {
            throw new NullPointerException("executor cannot be null");
        }
        this.executor = executor;
        return this;
    }

    /**
     * Specifies a custom scheduled executor for cleanup and refresh tasks.
     *
     * <p>By default, Kachi creates its own scheduled executor using virtual threads for:
     * <ul>
     *   <li>Periodic TTL cleanup (every 1 minute)</li>
     *   <li>Background refresh scheduling</li>
     * </ul>
     *
     * <p>This method allows you to provide a custom scheduler for these tasks.
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * ScheduledExecutorService customScheduler =
     *     Executors.newScheduledThreadPool(2);
     *
     * Cache<String, User> cache = CacheBuilder.newBuilder()
     *     .maximumSize(1000)
     *     .expireAfterWrite(10, TimeUnit.MINUTES)
     *     .scheduler(customScheduler)
     *     .build();
     * }</pre>
     *
     * <p><b>Warning:</b> The scheduler must remain running for the lifetime of the cache.
     * Shutting down the scheduler will stop cleanup and refresh operations.
     *
     * @param scheduler the scheduled executor to use for maintenance tasks
     * @return this builder instance
     */
    public CacheBuilder<K, V> scheduler(ScheduledExecutorService scheduler) {
        if (scheduler == null) {
            throw new NullPointerException("scheduler cannot be null");
        }
        this.scheduler = scheduler;
        return this;
    }

    /**
     * Specifies that keys should be wrapped in weak references.
     *
     * <p>Keys will be eligible for garbage collection when no strong references exist to them,
     * even though they are still in the cache. This is useful for canonicalizing mappings.
     *
     * <p><b>Warning:</b> When this method is used, the resulting cache will use identity (==)
     * comparison to determine equality of keys.
     *
     * <p><b>Note:</b> This feature is currently documented but not fully implemented in the
     * cache storage layer. It is reserved for future implementation.
     *
     * @return this builder instance
     */
    public CacheBuilder<K, V> weakKeys() {
        this.keyStrength = Strength.WEAK;
        return this;
    }

    /**
     * Specifies that values should be wrapped in weak references.
     *
     * <p>Values will be eligible for garbage collection when no strong references exist to them,
     * even though they are still in the cache.
     *
     * <p><b>Warning:</b> When this method is used, the resulting cache will use identity (==)
     * comparison to determine equality of values.
     *
     * <p><b>Note:</b> This feature is currently documented but not fully implemented in the
     * cache storage layer. It is reserved for future implementation.
     *
     * @return this builder instance
     */
    public CacheBuilder<K, V> weakValues() {
        this.valueStrength = Strength.WEAK;
        return this;
    }

    /**
     * Specifies that values should be wrapped in soft references.
     *
     * <p>Values will be eligible for garbage collection under memory pressure, but will be
     * retained as long as memory is available. The JVM will prefer to collect soft references
     * before throwing an {@link OutOfMemoryError}.
     *
     * <p>This is useful for memory-sensitive caches such as image caches or document caches
     * that can be reconstructed if needed.
     *
     * <p><b>Note:</b> This feature is currently documented but not fully implemented in the
     * cache storage layer. It is reserved for future implementation.
     *
     * @return this builder instance
     */
    public CacheBuilder<K, V> softValues() {
        this.valueStrength = Strength.SOFT;
        return this;
    }

    /**
     * Specifies a listener instance for eviction events (SIZE and EXPIRED removals only).
     *
     * <p>The {@link EvictionListener} is a specialized version of {@link RemovalListener} that
     * only receives notifications for automatic evictions, not for explicit invalidations or
     * replacements. Both listeners can be configured simultaneously.
     *
     * <p><b>Example - Resource Cleanup:</b>
     * <pre>{@code
     * Cache<String, FileHandle> cache = CacheBuilder.newBuilder()
     *     .maximumSize(100)
     *     .evictionListener((key, handle, cause) -> {
     *         handle.close();  // Clean up when evicted
     *     })
     *     .build();
     * }</pre>
     *
     * <p><b>Example - Multi-Level Cache:</b>
     * <pre>{@code
     * Cache<String, Data> l1Cache = CacheBuilder.newBuilder()
     *     .maximumSize(100)
     *     .evictionListener((key, data, cause) -> {
     *         l2Cache.put(key, data);  // Move to L2 when evicted from L1
     *     })
     *     .build();
     * }</pre>
     *
     * <p><b>Warning:</b> all exceptions thrown by {@code listener} will be logged and then swallowed.
     *
     * @param listener the eviction listener to use
     * @return this builder instance
     * @see EvictionListener
     * @see RemovalListener
     */
    public <K1 extends K, V1 extends V> CacheBuilder<K1, V1> evictionListener(
            EvictionListener<? super K1, ? super V1> listener) {
        if (listener == null) {
            throw new NullPointerException("eviction listener cannot be null");
        }
        @SuppressWarnings("unchecked")
        CacheBuilder<K1, V1> me = (CacheBuilder<K1, V1>) this;
        me.evictionListener = listener;
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
     * <p>The implementation returned depends on the configured strategy:
     * <ul>
     *   <li>{@link CacheStrategy#HIGH_PERFORMANCE} (default) - Fast, lock-free reads</li>
     *   <li>{@link CacheStrategy#PRECISION} - Accurate eviction, per-key locking</li>
     * </ul>
     *
     * @return a cache having the requested features
     */
    public <K1 extends K, V1 extends V> Cache<K1, V1> build() {
        return switch (strategy) {
            case HIGH_PERFORMANCE -> new HighPerformanceCacheImpl<K1, V1>(this);
            case PRECISION -> new PrecisionCacheImpl<K1, V1>(this);
        };
    }

    /**
     * Builds a cache, which either returns an already-loaded value for a given key or atomically
     * computes or retrieves it using the supplied {@code CacheLoader}. If another thread is currently
     * loading the value for this key, simply waits for that thread to finish and returns its loaded
     * value.
     *
     * <p>The implementation returned depends on the configured strategy:
     * <ul>
     *   <li>{@link CacheStrategy#HIGH_PERFORMANCE} (default) - Fast, lock-free reads</li>
     *   <li>{@link CacheStrategy#PRECISION} - Accurate eviction, per-key locking</li>
     * </ul>
     *
     * @param loader the cache loader used to obtain new values
     * @return a cache having the requested features
     */
    public <K1 extends K, V1 extends V> LoadingCache<K1, V1> build(CacheLoader<? super K1, V1> loader) {
        return switch (strategy) {
            case HIGH_PERFORMANCE -> new HighPerformanceCacheImpl<K1, V1>(this, loader);
            case PRECISION -> new PrecisionCacheImpl<K1, V1>(this, loader);
        };
    }

    /**
     * Builds an async cache which does not automatically load values when keys are requested.
     * All operations return {@link java.util.concurrent.CompletableFuture} for non-blocking access.
     *
     * <p>The async cache wraps a synchronous cache implementation. The implementation used depends
     * on the configured strategy:
     * <ul>
     *   <li>{@link CacheStrategy#HIGH_PERFORMANCE} (default) - Fast, lock-free reads</li>
     *   <li>{@link CacheStrategy#PRECISION} - Accurate eviction, per-key locking</li>
     * </ul>
     *
     * <p><b>Example usage:</b>
     * <pre>{@code
     * AsyncCache<String, User> cache = CacheBuilder.newBuilder()
     *     .maximumSize(1000)
     *     .expireAfterWrite(10, TimeUnit.MINUTES)
     *     .buildAsync();
     *
     * // Non-blocking get with async computation
     * CompletableFuture<User> future = cache.get("userId", key ->
     *     CompletableFuture.supplyAsync(() -> database.fetchUser(key))
     * );
     * }</pre>
     *
     * @return an async cache having the requested features
     */
    public <K1 extends K, V1 extends V> AsyncCache<K1, V1> buildAsync() {
        Cache<K1, V1> syncCache = build();
        return new AsyncCacheImpl<>(syncCache);
    }

    /**
     * Builds an async loading cache, which automatically loads values asynchronously using the
     * supplied {@link AsyncCacheLoader}. All operations return {@link java.util.concurrent.CompletableFuture}
     * for non-blocking access.
     *
     * <p>The async loading cache wraps a synchronous cache implementation. The implementation used
     * depends on the configured strategy:
     * <ul>
     *   <li>{@link CacheStrategy#HIGH_PERFORMANCE} (default) - Fast, lock-free reads</li>
     *   <li>{@link CacheStrategy#PRECISION} - Accurate eviction, per-key locking</li>
     * </ul>
     *
     * <p><b>Example usage:</b>
     * <pre>{@code
     * AsyncLoadingCache<String, User> cache = CacheBuilder.newBuilder()
     *     .maximumSize(1000)
     *     .expireAfterWrite(10, TimeUnit.MINUTES)
     *     .buildAsync((key, executor) ->
     *         CompletableFuture.supplyAsync(() -> database.fetchUser(key), executor)
     *     );
     *
     * // Non-blocking get with automatic loading
     * CompletableFuture<User> future = cache.get("userId");
     * }</pre>
     *
     * @param asyncLoader the async cache loader used to obtain new values
     * @return an async loading cache having the requested features
     */
    public <K1 extends K, V1 extends V> AsyncLoadingCache<K1, V1> buildAsync(
            AsyncCacheLoader<? super K1, V1> asyncLoader) {
        if (asyncLoader == null) {
            throw new NullPointerException("async loader cannot be null");
        }

        // Create a synchronous loader that wraps the async loader
        java.util.concurrent.Executor executor = AsyncLoadingCacheImpl.createDefaultExecutor();
        CacheLoader<K1, V1> syncLoader = AsyncLoadingCacheImpl.<K1, V1>toSyncLoader(asyncLoader, executor);

        // Build the synchronous loading cache
        LoadingCache<K1, V1> syncCache = build(syncLoader);

        // Wrap in async loading cache
        return new AsyncLoadingCacheImpl<K1, V1>(syncCache, asyncLoader, executor);
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

    public PutListener<? super K, ? super V> getPutListener() {
        return putListener;
    }

    public CacheWriter<? super K, ? super V> getCacheWriter() {
        return cacheWriter;
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

    public CacheStrategy getStrategy() {
        return strategy;
    }

    public Executor getExecutor() {
        return executor;
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public EvictionListener<? super K, ? super V> getEvictionListener() {
        return evictionListener;
    }

    public Strength getKeyStrength() {
        return keyStrength;
    }

    public Strength getValueStrength() {
        return valueStrength;
    }
}
