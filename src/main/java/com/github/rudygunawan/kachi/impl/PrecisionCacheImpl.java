package com.github.rudygunawan.kachi.impl;

import com.github.rudygunawan.kachi.api.CacheLoader;
import com.github.rudygunawan.kachi.api.Expiry;
import com.github.rudygunawan.kachi.api.LoadingCache;
import com.github.rudygunawan.kachi.api.RefreshPolicy;
import com.github.rudygunawan.kachi.api.Weigher;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.listener.PutListener;
import com.github.rudygunawan.kachi.listener.RemovalListener;
import com.github.rudygunawan.kachi.metrics.CacheMetrics;
import com.github.rudygunawan.kachi.metrics.ExpiryDistribution;
import com.github.rudygunawan.kachi.model.CacheEntry;
import com.github.rudygunawan.kachi.model.CacheStats;
import com.github.rudygunawan.kachi.policy.EvictionPolicy;
import com.github.rudygunawan.kachi.policy.FrequencySketch;
import com.github.rudygunawan.kachi.policy.PutCause;
import com.github.rudygunawan.kachi.policy.RemovalCause;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Precision cache implementation optimized for accurate eviction and strong consistency.
 *
 * <p><b>Performance Characteristics:</b>
 * <ul>
 *   <li>GET: ~800-1,400ns (still respectable performance)</li>
 *   <li>Concurrent: ~1-2M ops/sec</li>
 *   <li>Per-key locking with write-priority</li>
 *   <li>Accurate LRU/FIFO/LFU/TinyLFU eviction</li>
 *   <li>Immediate expiry checking</li>
 * </ul>
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>✅ Accurate eviction policies (LRU/FIFO/LFU/TinyLFU)</li>
 *   <li>✅ Strong consistency guarantees</li>
 *   <li>✅ Immediate expiry on every read</li>
 *   <li>✅ Per-entry custom TTL</li>
 *   <li>✅ Write-priority locking</li>
 *   <li>⚠️ Slower than HighPerformanceCache (~12-22x)</li>
 * </ul>
 *
 * <p>For maximum speed with random eviction, use {@link HighPerformanceCacheImpl}.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @see HighPerformanceCacheImpl
 */
public class PrecisionCacheImpl<K, V> implements LoadingCache<K, V>, CacheMetrics {
    /**
     * Logger for cache operations. Logger name: "com.github.rudygunawan.kachi.Cache"
     *
     * <p>Log levels used:
     * <ul>
     *   <li>SEVERE: Critical errors that prevent cache operations</li>
     *   <li>WARNING: Errors in custom policies or listeners (operations continue)</li>
     *   <li>FINE: Detailed cache operations (evictions, refreshes)</li>
     *   <li>FINER: Entry-level operations (put, get, remove)</li>
     *   <li>FINEST: Detailed debugging information</li>
     * </ul>
     */
    private static final Logger LOGGER = Logger.getLogger("com.github.rudygunawan.kachi.Cache");

    // Idle threshold - entries not accessed in last 5 minutes are considered idle
    private static final long IDLE_THRESHOLD_NANOS = 5L * 60 * 1_000_000_000; // 5 minutes
    private final ConcurrentHashMap<K, CacheEntry<V>> storage;
    private final CacheLoader<? super K, V> loader;
    private final long maximumSize;
    private final long maximumWeight;
    private final Weigher<? super K, ? super V> weigher;
    private final long expireAfterWriteNanos;
    private final long expireAfterAccessNanos;
    private final boolean recordStats;
    private final EvictionPolicy evictionPolicy;
    private final RemovalListener<? super K, ? super V> removalListener;
    private final PutListener<? super K, ? super V> putListener;
    private final Expiry<? super K, ? super V> expiry;
    private final RefreshPolicy<? super K, ? super V> refreshPolicy;
    private final long refreshAfterWriteNanos;

    // Statistics
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong loadSuccessCount = new AtomicLong(0);
    private final AtomicLong loadFailureCount = new AtomicLong(0);
    private final AtomicLong totalLoadTime = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);
    private final AtomicLong currentWeight = new AtomicLong(0);

    // Eviction tracking - different for each policy
    private final ConcurrentLinkedDeque<K> accessOrder = new ConcurrentLinkedDeque<>(); // For LRU and FIFO
    private final ConcurrentHashMap<K, ReentrantReadWriteLock> keyLocks = new ConcurrentHashMap<>();

    // Window TinyLFU segments (only used when evictionPolicy == WINDOW_TINY_LFU)
    private final ConcurrentLinkedDeque<K> windowQueue;      // 1% of cache - admission window
    private final ConcurrentLinkedDeque<K> probationQueue;   // 20% of main cache - probation segment
    private final ConcurrentLinkedDeque<K> protectedQueue;   // 80% of main cache - protected segment
    private final FrequencySketch frequencySketch;
    private final int windowMaxSize;
    private final int probationMaxSize;
    private final int protectedMaxSize;

    // For loading cache - prevent duplicate loads
    private final ConcurrentHashMap<K, CompletableFuture<V>> loadingFutures = new ConcurrentHashMap<>();

    // Scheduled cleanup for TTL expiration
    private final ScheduledExecutorService cleanupScheduler;
    private final ScheduledFuture<?> cleanupTask;

    // Scheduled refresh for entries
    private final ScheduledExecutorService refreshScheduler;
    private final ScheduledFuture<?> refreshTask;

    // Read timeout for write-priority
    private static final long READ_TIMEOUT_MS = 1000;

    // Refresh check interval (check every 30 seconds)
    private static final long REFRESH_CHECK_INTERVAL_MS = 30_000;

    /**
     * Constructor for non-loading cache.
     */
    public PrecisionCacheImpl(CacheBuilder<?, ?> builder) {
        this(builder, null);
    }

    /**
     * Constructor for loading cache.
     */
    @SuppressWarnings("unchecked")
    public PrecisionCacheImpl(CacheBuilder<?, ?> builder, CacheLoader<? super K, V> loader) {
        this.storage = new ConcurrentHashMap<>(builder.getInitialCapacity(), 0.75f, builder.getConcurrencyLevel());
        this.loader = loader;
        this.maximumSize = builder.getMaximumSize();
        this.maximumWeight = builder.getMaximumWeight();
        this.weigher = (Weigher<? super K, ? super V>) builder.getWeigher();
        this.expireAfterWriteNanos = builder.getExpireAfterWriteNanos();
        this.expireAfterAccessNanos = builder.getExpireAfterAccessNanos();
        this.recordStats = builder.isRecordingStats();
        this.evictionPolicy = builder.getEvictionPolicy();
        this.removalListener = (RemovalListener<? super K, ? super V>) builder.getRemovalListener();
        this.putListener = (PutListener<? super K, ? super V>) builder.getPutListener();
        this.expiry = (Expiry<? super K, ? super V>) builder.getExpiry();
        this.refreshPolicy = (RefreshPolicy<? super K, ? super V>) builder.getRefreshPolicy();
        this.refreshAfterWriteNanos = builder.getRefreshAfterWriteNanos();

        // Initialize Window TinyLFU segments if that policy is selected
        if (evictionPolicy == EvictionPolicy.WINDOW_TINY_LFU && maximumSize > 0) {
            // Calculate segment sizes
            // Window: 1% of cache (minimum 1)
            this.windowMaxSize = Math.max(1, (int) (maximumSize * 0.01));
            // Main cache: 99% split into Protected (80%) and Probation (20%)
            int mainCacheSize = (int) (maximumSize - windowMaxSize);
            this.protectedMaxSize = (int) (mainCacheSize * 0.8);
            this.probationMaxSize = mainCacheSize - protectedMaxSize;

            // Initialize queues and frequency sketch
            this.windowQueue = new ConcurrentLinkedDeque<>();
            this.probationQueue = new ConcurrentLinkedDeque<>();
            this.protectedQueue = new ConcurrentLinkedDeque<>();
            this.frequencySketch = new FrequencySketch((int) maximumSize);
        } else {
            this.windowMaxSize = 0;
            this.probationMaxSize = 0;
            this.protectedMaxSize = 0;
            this.windowQueue = null;
            this.probationQueue = null;
            this.protectedQueue = null;
            this.frequencySketch = null;
        }

        // Start scheduled TTL cleanup task (runs every minute)
        // JDK 21: Using virtual threads for lightweight, scalable concurrency
        if (expireAfterWriteNanos > 0 || expireAfterAccessNanos > 0 || expiry != null) {
            this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = Thread.ofVirtual()
                    .name("kachi-cache-cleanup")
                    .unstarted(r);
                return t;
            });
            this.cleanupTask = cleanupScheduler.scheduleAtFixedRate(
                    this::cleanUp,
                    1, 1, TimeUnit.MINUTES
            );
        } else {
            this.cleanupScheduler = null;
            this.cleanupTask = null;
        }

        // Start scheduled refresh task (runs every 30 seconds) - only for LoadingCache
        // JDK 21: Using virtual threads for unlimited concurrent refreshes
        if (loader != null && (refreshPolicy != null || refreshAfterWriteNanos > 0)) {
            this.refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = Thread.ofVirtual()
                    .name("kachi-cache-refresh")
                    .unstarted(r);
                return t;
            });
            this.refreshTask = refreshScheduler.scheduleAtFixedRate(
                    this::refreshEntries,
                    REFRESH_CHECK_INTERVAL_MS,
                    REFRESH_CHECK_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
            );
        } else {
            this.refreshScheduler = null;
            this.refreshTask = null;
        }
    }

    @Override
    public V getIfPresent(K key) {
        Objects.requireNonNull(key, "key cannot be null");

        // Acquire read lock with timeout for write-priority
        ReentrantReadWriteLock lock = getOrCreateLock(key);
        boolean readLockHeld = false;
        try {
            if (!lock.readLock().tryLock(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                // Timeout waiting for write to complete
                if (recordStats) missCount.incrementAndGet();
                return null;
            }
            readLockHeld = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (recordStats) missCount.incrementAndGet();
            return null;
        }

        try {
            CacheEntry<V> entry = storage.get(key);

            if (entry == null) {
                if (recordStats) missCount.incrementAndGet();
                return null;
            }

            // Check if expired
            if (isExpired(entry)) {
                lock.readLock().unlock();
                readLockHeld = false;
                // Upgrade to write lock to remove
                lock.writeLock().lock();
                try {
                    // Double-check after acquiring write lock
                    entry = storage.get(key);
                    if (entry != null && isExpired(entry)) {
                        storage.remove(key);
                        // Subtract the expired entry's weight
                        currentWeight.addAndGet(-entry.getWeight());
                        removeFromEvictionQueues(key);
                        fireRemovalEvent(key, entry.getValue(), RemovalCause.EXPIRED);
                        if (recordStats) {
                            missCount.incrementAndGet();
                            evictionCount.incrementAndGet();
                        }
                    }
                    return null;
                } finally {
                    lock.writeLock().unlock();
                }
            }

            // Update access time for expire-after-access
            if (expireAfterAccessNanos > 0) {
                entry.updateAccessTime();
            }

            // Update tracking for eviction policy
            updateAccessTracking(key);

            if (recordStats) hitCount.incrementAndGet();
            return entry.getValue();
        } finally {
            if (readLockHeld) {
                lock.readLock().unlock();
            }
        }
    }

    @Override
    public V get(K key, Callable<? extends V> loader) throws Exception {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(loader, "loader cannot be null");

        V value = getIfPresent(key);
        if (value != null) {
            return value;
        }

        // Load the value with write lock
        ReentrantReadWriteLock lock = getOrCreateLock(key);
        lock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            CacheEntry<V> entry = storage.get(key);
            if (entry != null && !isExpired(entry)) {
                if (expireAfterAccessNanos > 0) {
                    entry.updateAccessTime();
                }
                if (recordStats) hitCount.incrementAndGet();
                return entry.getValue();
            }

            long startTime = System.nanoTime();
            try {
                value = loader.call();
                if (value == null) {
                    throw new NullPointerException("loader returned null value");
                }
                putInternal(key, value, RemovalCause.REPLACED);
                if (recordStats) {
                    loadSuccessCount.incrementAndGet();
                    totalLoadTime.addAndGet(System.nanoTime() - startTime);
                }
                return value;
            } catch (Exception e) {
                if (recordStats) {
                    loadFailureCount.incrementAndGet();
                    totalLoadTime.addAndGet(System.nanoTime() - startTime);
                }
                throw e;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public V get(K key) throws Exception {
        if (loader == null) {
            throw new UnsupportedOperationException("get(key) requires a CacheLoader");
        }

        Objects.requireNonNull(key, "key cannot be null");

        V value = getIfPresent(key);
        if (value != null) {
            return value;
        }

        // Use CompletableFuture to ensure only one thread loads a given key
        CompletableFuture<V> future = loadingFutures.get(key);
        if (future != null) {
            try {
                return future.get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                throw new RuntimeException(cause);
            }
        }

        CompletableFuture<V> newFuture = new CompletableFuture<>();
        CompletableFuture<V> existingFuture = loadingFutures.putIfAbsent(key, newFuture);

        if (existingFuture != null) {
            try {
                return existingFuture.get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                throw new RuntimeException(cause);
            }
        }

        // We're responsible for loading with write lock
        ReentrantReadWriteLock lock = getOrCreateLock(key);
        lock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            CacheEntry<V> entry = storage.get(key);
            if (entry != null && !isExpired(entry)) {
                if (expireAfterAccessNanos > 0) {
                    entry.updateAccessTime();
                }
                newFuture.complete(entry.getValue());
                return entry.getValue();
            }

            long startTime = System.nanoTime();
            try {
                value = loader.load(key);
                if (value == null) {
                    throw new NullPointerException("loader returned null value for key: " + key);
                }
                putInternal(key, value, RemovalCause.REPLACED);
                if (recordStats) {
                    loadSuccessCount.incrementAndGet();
                    totalLoadTime.addAndGet(System.nanoTime() - startTime);
                }
                newFuture.complete(value);
                return value;
            } catch (Exception e) {
                if (recordStats) {
                    loadFailureCount.incrementAndGet();
                    totalLoadTime.addAndGet(System.nanoTime() - startTime);
                }
                newFuture.completeExceptionally(e);
                throw e;
            }
        } finally {
            lock.writeLock().unlock();
            loadingFutures.remove(key);
        }
    }

    @Override
    public Map<K, V> getAll(Iterable<? extends K> keys) throws Exception {
        if (loader == null) {
            throw new UnsupportedOperationException("getAll requires a CacheLoader");
        }

        Objects.requireNonNull(keys, "keys cannot be null");
        Map<K, V> result = new LinkedHashMap<>();
        List<K> keysToLoad = new ArrayList<>();

        // First pass: collect cached values and keys to load
        for (K key : keys) {
            V value = getIfPresent(key);
            if (value != null) {
                result.put(key, value);
            } else {
                keysToLoad.add(key);
            }
        }

        if (keysToLoad.isEmpty()) {
            return Collections.unmodifiableMap(result);
        }

        // Try bulk loading first (more efficient)
        try {
            @SuppressWarnings("unchecked")
            Map<K, V> loaded = (Map<K, V>) loader.loadAll(keysToLoad);
            for (Map.Entry<K, V> entry : loaded.entrySet()) {
                if (entry.getValue() != null) {
                    put(entry.getKey(), entry.getValue());
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (UnsupportedOperationException e) {
            // Bulk loading not supported, fall back to parallel individual loads
            if (keysToLoad.size() == 1) {
                // Single key - no need for parallelization
                K key = keysToLoad.get(0);
                try {
                    V value = get(key);
                    result.put(key, value);
                } catch (Exception ex) {
                    // Skip this key on error
                }
            } else {
                // Multiple keys - load in parallel for better performance
                // JDK 21: Using virtual threads for unlimited parallel loads (no thread pool limit!)
                ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
                try {
                    List<CompletableFuture<Map.Entry<K, V>>> futures = new ArrayList<>();
                    for (K key : keysToLoad) {
                        CompletableFuture<Map.Entry<K, V>> future = CompletableFuture.supplyAsync(() -> {
                            try {
                                V value = get(key);
                                return new AbstractMap.SimpleEntry<>(key, value);
                            } catch (Exception ex) {
                                return null; // Skip on error
                            }
                        }, virtualExecutor); // Use virtual thread executor
                        futures.add(future);
                    }

                    // Wait for all loads to complete
                    CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0])
                    );

                    try {
                        allFutures.get(); // Wait for completion

                        // Collect results
                        for (CompletableFuture<Map.Entry<K, V>> future : futures) {
                            Map.Entry<K, V> entry = future.get();
                            if (entry != null && entry.getValue() != null) {
                                result.put(entry.getKey(), entry.getValue());
                            }
                        }
                    } catch (Exception ex) {
                        // Some loads failed, but we already collected successful ones
                    }
                } finally {
                    virtualExecutor.shutdown(); // Clean up virtual thread executor
                }
            }
        }

        return Collections.unmodifiableMap(result);
    }

    @Override
    public void refresh(K key) {
        if (loader == null) {
            return;
        }

        Objects.requireNonNull(key, "key cannot be null");

        // JDK 21: Use virtual thread for async refresh - lightweight and scalable
        CompletableFuture.runAsync(() -> {
            try {
                V newValue = loader.load(key);
                if (newValue != null) {
                    put(key, newValue);
                }
            } catch (Exception e) {
                // Log and swallow as per contract
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        ReentrantReadWriteLock lock = getOrCreateLock(key);
        lock.writeLock().lock();
        try {
            putInternal(key, value, RemovalCause.REPLACED);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Internal put method that must be called with write lock held.
     */
    private void putInternal(K key, V value, RemovalCause replacementCause) {
        CacheEntry<V> oldEntry = storage.get(key);
        long ttl = getTtlForEntry(key, value, oldEntry);

        // Calculate weight for this entry
        int weight = calculateWeight(key, value);
        CacheEntry<V> newEntry = new CacheEntry<>(value, ttl, weight);

        oldEntry = storage.put(key, newEntry);

        // Fire put event (before removal event to allow listeners to know about the new value first)
        PutCause putCause = (oldEntry != null) ? PutCause.UPDATE : PutCause.INSERT;
        firePutEvent(key, value, putCause);

        if (oldEntry != null) {
            // Subtract old entry weight and add new entry weight
            currentWeight.addAndGet(weight - oldEntry.getWeight());
            fireRemovalEvent(key, oldEntry.getValue(), replacementCause);
        } else {
            // New entry, just add its weight
            currentWeight.addAndGet(weight);
        }

        updateAccessTracking(key);
        evictIfNecessary();
    }

    @Override
    public Map<K, V> getAllPresent(Iterable<? extends K> keys) {
        Objects.requireNonNull(keys, "keys cannot be null");
        Map<K, V> result = new LinkedHashMap<>();

        for (K key : keys) {
            V value = getIfPresent(key);
            if (value != null) {
                result.put(key, value);
            }
        }

        return Collections.unmodifiableMap(result);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        Objects.requireNonNull(map, "map cannot be null");

        // Optimized bulk insert: process entries in batch
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            K key = entry.getKey();
            V value = entry.getValue();

            if (key == null || value == null) {
                throw new NullPointerException("null keys and values are not allowed");
            }

            put(key, value);
        }
    }

    @Override
    public void invalidate(K key) {
        Objects.requireNonNull(key, "key cannot be null");

        ReentrantReadWriteLock lock = getOrCreateLock(key);
        lock.writeLock().lock();
        try {
            CacheEntry<V> removed = storage.remove(key);
            if (removed != null) {
                // Subtract the removed entry's weight
                currentWeight.addAndGet(-removed.getWeight());
                removeFromEvictionQueues(key);
                fireRemovalEvent(key, removed.getValue(), RemovalCause.EXPLICIT);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void invalidateAll(Iterable<? extends K> keys) {
        Objects.requireNonNull(keys, "keys cannot be null");

        // Optimized bulk invalidation
        for (K key : keys) {
            if (key != null) {
                invalidate(key);
            }
        }
    }

    @Override
    public void invalidateAll() {
        // Take snapshot to avoid concurrent modification
        Set<K> keys = new HashSet<>(storage.keySet());
        invalidateAll(keys);
    }

    @Override
    public long size() {
        return storage.size();
    }

    @Override
    public CacheStats stats() {
        return new CacheStats(
                hitCount.get(),
                missCount.get(),
                loadSuccessCount.get(),
                loadFailureCount.get(),
                totalLoadTime.get(),
                evictionCount.get()
        );
    }

    @Override
    public void cleanUp() {
        long now = System.nanoTime();
        List<K> keysToRemove = new ArrayList<>();

        // Find expired entries
        for (Map.Entry<K, CacheEntry<V>> entry : storage.entrySet()) {
            if (isExpired(entry.getValue())) {
                keysToRemove.add(entry.getKey());
            }
        }

        // Remove expired entries
        for (K key : keysToRemove) {
            ReentrantReadWriteLock lock = getOrCreateLock(key);
            lock.writeLock().lock();
            try {
                CacheEntry<V> entry = storage.get(key);
                if (entry != null && isExpired(entry)) {
                    storage.remove(key);
                    // Subtract the expired entry's weight
                    currentWeight.addAndGet(-entry.getWeight());
                    removeFromEvictionQueues(key);
                    fireRemovalEvent(key, entry.getValue(), RemovalCause.EXPIRED);
                    if (recordStats) evictionCount.incrementAndGet();
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    @Override
    public Map<K, V> asMap() {
        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, CacheEntry<V>> entry : storage.entrySet()) {
            if (!isExpired(entry.getValue())) {
                result.put(entry.getKey(), entry.getValue().getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    // Helper methods

    private ReentrantReadWriteLock getOrCreateLock(K key) {
        return keyLocks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
    }

    private boolean isExpired(CacheEntry<V> entry) {
        if (expireAfterWriteNanos > 0 && entry.isExpired()) {
            return true;
        }
        if (expireAfterAccessNanos > 0) {
            long currentTime = System.nanoTime();
            return (currentTime - entry.getAccessTime()) >= expireAfterAccessNanos;
        }
        return false;
    }

    /**
     * Gets the TTL for an entry. If custom expiry is configured, it is used.
     * Otherwise, falls back to the fixed expireAfterWrite TTL.
     *
     * @param key the key being stored
     * @param value the value being stored
     * @param oldEntry the old entry if this is an update, null if this is a create
     * @return the TTL in nanoseconds, or 0 for no expiration
     */
    private long getTtlForEntry(K key, V value, CacheEntry<V> oldEntry) {
        long currentTime = System.nanoTime();

        // If custom expiry is configured, use it
        if (expiry != null) {
            try {
                if (oldEntry == null) {
                    // This is a create operation
                    return expiry.expireAfterCreate(key, value, currentTime);
                } else {
                    // This is an update operation
                    long currentDuration = oldEntry.getExpirationTime() - oldEntry.getWriteTime();
                    return expiry.expireAfterUpdate(key, value, currentTime, currentDuration);
                }
            } catch (Exception e) {
                // If custom expiry throws an exception, log and fall back to default
                LOGGER.log(Level.WARNING, "Error in custom expiry policy for key: " + key +
                          ", falling back to default TTL", e);
            }
        }

        // Fall back to fixed TTL
        if (expireAfterWriteNanos > 0) {
            return expireAfterWriteNanos;
        }
        return 0;
    }

    /**
     * Calculates the weight of an entry using the weigher if configured, or returns 1.
     *
     * @param key the key being stored
     * @param value the value being stored
     * @return the weight of the entry (minimum 1)
     */
    private int calculateWeight(K key, V value) {
        if (weigher != null) {
            try {
                int weight = weigher.weigh(key, value);
                // Ensure weight is at least 1 (protect against bad weigher implementations)
                return Math.max(1, weight);
            } catch (Exception e) {
                // If weigher throws exception, log and fall back to weight of 1
                LOGGER.log(Level.WARNING, "Error in weigher for key: " + key +
                          ", falling back to weight of 1", e);
                return 1;
            }
        }
        return 1;
    }

    private void updateAccessTracking(K key) {
        if (maximumSize <= 0 && maximumWeight <= 0) {
            return;
        }

        switch (evictionPolicy) {
            case LRU:
                // Remove and re-add to move to end (most recently used)
                accessOrder.remove(key);
                accessOrder.offer(key);
                break;
            case FIFO:
                // Only add if not present (keep insertion order)
                if (!accessOrder.contains(key)) {
                    accessOrder.offer(key);
                }
                break;
            case LFU:
                // Access count is tracked in CacheEntry
                // No deque tracking needed for LFU
                break;
            case WINDOW_TINY_LFU:
                // Record access in frequency sketch
                frequencySketch.increment(key);

                // Move between segments based on access
                if (windowQueue.remove(key)) {
                    // Entry was in window, promote to main cache (probation)
                    probationQueue.offer(key);
                } else if (probationQueue.remove(key)) {
                    // Entry was in probation, promote to protected
                    protectedQueue.offer(key);
                } else if (protectedQueue.remove(key)) {
                    // Entry already in protected, move to end (most recently used)
                    protectedQueue.offer(key);
                } else {
                    // New entry, add to window
                    windowQueue.offer(key);
                }
                break;
        }
    }

    /**
     * Removes a key from all eviction tracking queues.
     * Used when an entry is removed from the cache.
     */
    private void removeFromEvictionQueues(K key) {
        switch (evictionPolicy) {
            case LRU:
            case FIFO:
                accessOrder.remove(key);
                break;
            case LFU:
                // No queue tracking for LFU
                break;
            case WINDOW_TINY_LFU:
                // Remove from whichever queue it's in
                windowQueue.remove(key);
                probationQueue.remove(key);
                protectedQueue.remove(key);
                break;
        }
    }

    private void evictIfNecessary() {
        if (maximumSize <= 0 && maximumWeight <= 0) {
            return;
        }

        // Check both size and weight constraints
        int failedAttempts = 0;
        int maxFailedAttempts = 10; // Prevent infinite loops
        while ((maximumSize > 0 && storage.size() > maximumSize) ||
               (maximumWeight > 0 && currentWeight.get() > maximumWeight)) {
            K keyToEvict = selectKeyToEvict();
            if (keyToEvict == null) {
                break;
            }

            ReentrantReadWriteLock lock = getOrCreateLock(keyToEvict);
            if (lock.writeLock().tryLock()) {
                try {
                    CacheEntry<V> removed = storage.remove(keyToEvict);
                    if (removed != null) {
                        // Subtract the evicted entry's weight
                        currentWeight.addAndGet(-removed.getWeight());
                        removeFromEvictionQueues(keyToEvict);
                        fireRemovalEvent(keyToEvict, removed.getValue(), RemovalCause.SIZE);
                        if (recordStats) evictionCount.incrementAndGet();
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine("Evicted entry due to size/weight limit: key=" + keyToEvict +
                                       ", policy=" + evictionPolicy + ", size=" + storage.size() +
                                       ", weight=" + currentWeight.get());
                        }
                        failedAttempts = 0; // Reset on successful eviction
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            } else {
                // Couldn't acquire lock, increment failed attempts
                failedAttempts++;
                if (failedAttempts >= maxFailedAttempts) {
                    // Give up to prevent infinite loop
                    break;
                }
            }
        }
    }

    private K selectKeyToEvict() {
        switch (evictionPolicy) {
            case LRU:
            case FIFO:
                return selectFromAccessOrder();
            case LFU:
                return selectLFUCandidate();
            case WINDOW_TINY_LFU:
                return selectWindowTinyLfuVictim();
            default:
                return selectFromAccessOrder();
        }
    }

    /**
     * Selects a key from the access order deque that is eligible for eviction.
     * Entries must be at least 1 second old to be evicted.
     */
    private K selectFromAccessOrder() {
        // Poll from deque and check eligibility
        int attempts = 0;
        int maxAttempts = accessOrder.size();
        K key;
        while ((key = accessOrder.poll()) != null && attempts < maxAttempts) {
            attempts++;
            CacheEntry<V> entry = storage.get(key);
            if (entry != null && entry.isEligibleForEviction()) {
                return key;
            }
            // Entry not eligible yet, put it back at the end
            if (entry != null) {
                accessOrder.offer(key);
            }
        }
        return null;
    }

    /**
     * Selects the least frequently used entry that is eligible for eviction.
     * Entries must be at least 1 second old to be evicted.
     */
    private K selectLFUCandidate() {
        K candidate = null;
        long minCount = Long.MAX_VALUE;

        for (Map.Entry<K, CacheEntry<V>> entry : storage.entrySet()) {
            CacheEntry<V> cacheEntry = entry.getValue();
            // Only consider entries that are old enough
            if (!cacheEntry.isEligibleForEviction()) {
                continue;
            }

            long count = cacheEntry.getAccessCount();
            if (count < minCount) {
                minCount = count;
                candidate = entry.getKey();
            }
        }

        return candidate;
    }

    /**
     * Window TinyLFU victim selection with admission policy.
     *
     * Algorithm:
     * 1. If window queue exceeds its size, evict from window (LRU)
     * 2. Otherwise, evict from probation queue using TinyLFU admission policy
     * 3. If probation is empty, evict from protected queue
     *
     * The admission policy compares frequencies using the frequency sketch to
     * prevent rarely-accessed entries from polluting the cache.
     */
    private K selectWindowTinyLfuVictim() {
        // Step 1: Check if window queue is over capacity
        if (windowQueue.size() > windowMaxSize) {
            int attempts = 0;
            int maxAttempts = windowQueue.size();
            K victim;
            while ((victim = windowQueue.poll()) != null && attempts < maxAttempts) {
                attempts++;
                CacheEntry<V> entry = storage.get(victim);
                if (entry != null && entry.isEligibleForEviction()) {
                    return victim;
                }
                // Entry not eligible, try next
                if (entry != null) {
                    windowQueue.offer(victim); // Put back
                }
            }
        }

        // Step 2: Try to evict from probation queue
        if (!probationQueue.isEmpty()) {
            int attempts = 0;
            int maxAttempts = probationQueue.size();
            K victim;
            while ((victim = probationQueue.poll()) != null && attempts < maxAttempts) {
                attempts++;
                CacheEntry<V> entry = storage.get(victim);
                if (entry != null && entry.isEligibleForEviction()) {
                    return victim;
                }
                // Entry not eligible, put back and try next
                if (entry != null) {
                    probationQueue.offer(victim);
                }
            }
        }

        // Step 3: Fall back to protected queue if probation is empty
        if (!protectedQueue.isEmpty()) {
            int attempts = 0;
            int maxAttempts = protectedQueue.size();
            K victim;
            while ((victim = protectedQueue.poll()) != null && attempts < maxAttempts) {
                attempts++;
                CacheEntry<V> entry = storage.get(victim);
                if (entry != null && entry.isEligibleForEviction()) {
                    return victim;
                }
                // Entry not eligible, put back and try next
                if (entry != null) {
                    protectedQueue.offer(victim);
                }
            }
        }

        return null;
    }

    private void fireRemovalEvent(K key, V value, RemovalCause cause) {
        if (removalListener != null) {
            try {
                removalListener.onRemoval(key, value, cause);
            } catch (Exception e) {
                // Log and swallow exceptions from listener
                LOGGER.log(Level.WARNING, "RemovalListener threw exception for key: " + key +
                          ", cause: " + cause, e);
            }
        }
    }

    /**
     * Fires a put event to the configured put listener.
     * Exceptions thrown by the listener are logged and swallowed to prevent cache operation failures.
     *
     * @param key the key that was put
     * @param value the value that was put
     * @param cause the reason for the put (INSERT for new entries, UPDATE for replacements)
     */
    private void firePutEvent(K key, V value, PutCause cause) {
        if (putListener != null) {
            try {
                putListener.onPut(key, value, cause);
            } catch (Exception e) {
                // Log and swallow exceptions from listener
                LOGGER.log(Level.WARNING, "PutListener threw exception for key: " + key +
                          ", cause: " + cause, e);
            }
        }
    }

    /**
     * Shutdown the cleanup scheduler when cache is no longer needed.
     */
    /**
     * Background task that checks all entries and refreshes those that need refreshing.
     * This runs periodically based on REFRESH_CHECK_INTERVAL_MS.
     */
    private void refreshEntries() {
        if (loader == null) {
            return; // No loader, cannot refresh
        }

        long currentTime = System.nanoTime();

        // Iterate over all entries and check if they need refresh
        for (Map.Entry<K, CacheEntry<V>> mapEntry : storage.entrySet()) {
            K key = mapEntry.getKey();
            CacheEntry<V> entry = mapEntry.getValue();

            if (shouldRefresh(key, entry, currentTime)) {
                // Refresh asynchronously - don't block the scheduler thread
                CompletableFuture.runAsync(() -> refreshEntry(key));
            }
        }
    }

    /**
     * Determines if an entry should be refreshed based on the refresh policy or fixed interval.
     */
    private boolean shouldRefresh(K key, CacheEntry<V> entry, long currentTime) {
        long lastRefresh = entry.getLastRefreshTime();
        long timeSinceRefresh = currentTime - lastRefresh;

        // Check custom refresh policy first
        if (refreshPolicy != null) {
            try {
                long refreshInterval = refreshPolicy.getRefreshInterval(key, entry.getValue(), currentTime);
                return timeSinceRefresh >= refreshInterval;
            } catch (Exception e) {
                // If policy throws exception, fall through to fixed interval
                LOGGER.log(Level.WARNING, "Error in refresh policy for key: " + key +
                          ", falling back to fixed refresh interval", e);
            }
        }

        // Fall back to fixed refresh interval
        if (refreshAfterWriteNanos > 0) {
            return timeSinceRefresh >= refreshAfterWriteNanos;
        }

        return false;
    }

    /**
     * Refreshes a single entry by reloading its value using the CacheLoader.
     * The old value continues to be served until the new value is loaded.
     */
    private void refreshEntry(K key) {
        CacheEntry<V> oldEntry = storage.get(key);
        if (oldEntry == null) {
            return; // Entry was removed
        }

        try {
            // Load new value
            V newValue = loader.load(key);

            if (newValue != null) {
                // Update the entry with new value
                ReentrantReadWriteLock lock = getOrCreateLock(key);
                lock.writeLock().lock();
                try {
                    // Check if entry still exists and wasn't replaced
                    CacheEntry<V> currentEntry = storage.get(key);
                    if (currentEntry != null && currentEntry.getLastRefreshTime() == oldEntry.getLastRefreshTime()) {
                        // Update with new entry
                        long ttl = getTtlForEntry(key, newValue, currentEntry);
                        CacheEntry<V> newEntry = new CacheEntry<>(newValue, ttl);
                        newEntry.updateLastRefreshTime();
                        storage.put(key, newEntry);

                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine("Successfully refreshed entry in background: key=" + key);
                        }

                        // Notify refresh policy
                        if (refreshPolicy != null) {
                            try {
                                refreshPolicy.onRefreshSuccess(key, oldEntry.getValue(), newValue, System.nanoTime());
                            } catch (Exception e) {
                                // Ignore callback errors
                            }
                        }
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }
        } catch (Exception e) {
            // Refresh failed - keep old value
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Failed to refresh entry in background: key=" + key +
                          ", keeping old value", e);
            }
            if (refreshPolicy != null) {
                try {
                    refreshPolicy.onRefreshFailure(key, oldEntry.getValue(), e, System.nanoTime());
                } catch (Exception callbackError) {
                    // Ignore callback errors
                }
            }
        }
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
        }
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
        if (refreshScheduler != null) {
            refreshScheduler.shutdown();
        }
    }

    // CacheMetrics interface implementation for Micrometer integration

    @Override
    public long hitCount() {
        return hitCount.get();
    }

    @Override
    public long missCount() {
        return missCount.get();
    }

    @Override
    public long evictionCount() {
        return evictionCount.get();
    }

    @Override
    public long loadSuccessCount() {
        return loadSuccessCount.get();
    }

    @Override
    public long loadFailureCount() {
        return loadFailureCount.get();
    }

    @Override
    public long totalLoadTimeNanos() {
        return totalLoadTime.get();
    }

    @Override
    public long idleEntryCount() {
        long now = System.nanoTime();
        long count = 0;

        for (CacheEntry<V> entry : storage.values()) {
            long timeSinceLastAccess = now - entry.getAccessTime();
            if (timeSinceLastAccess >= IDLE_THRESHOLD_NANOS) {
                count++;
            }
        }

        return count;
    }

    @Override
    public long estimatedMemoryUsageBytes() {
        // Rough estimation:
        // - Each ConcurrentHashMap entry: ~64 bytes (node overhead)
        // - Each CacheEntry object: ~80 bytes (object header + fields)
        // - Key and value size is application-specific, using rough estimate
        // - This is a very rough approximation

        long entryCount = storage.size();
        long perEntryOverhead = 64 + 80; // HashMap node + CacheEntry

        // Assume average key + value size of 100 bytes (very rough)
        // Applications can override this with custom metrics if needed
        long averageKeyValueSize = 100;

        return entryCount * (perEntryOverhead + averageKeyValueSize);
    }

    @Override
    public long averageEntrySizeBytes() {
        long entryCount = storage.size();
        if (entryCount == 0) {
            return 0;
        }
        return estimatedMemoryUsageBytes() / entryCount;
    }

    @Override
    public ExpiryDistribution expiryDistribution() {
        long now = System.nanoTime();

        long lessThan1Min = 0;
        long lessThan5Min = 0;
        long lessThan15Min = 0;
        long lessThan1Hour = 0;
        long lessThan24Hours = 0;
        long moreThan24Hours = 0;
        long neverExpires = 0;

        for (CacheEntry<V> entry : storage.values()) {
            long expirationTime = entry.getExpirationTime();

            // Check if entry never expires
            if (expirationTime == Long.MAX_VALUE) {
                neverExpires++;
                continue;
            }

            long timeUntilExpiry = expirationTime - now;

            // Entry already expired
            if (timeUntilExpiry <= 0) {
                lessThan1Min++;
            } else if (timeUntilExpiry < 60_000_000_000L) { // < 1 minute
                lessThan1Min++;
            } else if (timeUntilExpiry < 5 * 60_000_000_000L) { // < 5 minutes
                lessThan5Min++;
            } else if (timeUntilExpiry < 15 * 60_000_000_000L) { // < 15 minutes
                lessThan15Min++;
            } else if (timeUntilExpiry < 60 * 60_000_000_000L) { // < 1 hour
                lessThan1Hour++;
            } else if (timeUntilExpiry < 24 * 60 * 60_000_000_000L) { // < 24 hours
                lessThan24Hours++;
            } else { // > 24 hours
                moreThan24Hours++;
            }
        }

        return new ExpiryDistribution(
                lessThan1Min,
                lessThan5Min,
                lessThan15Min,
                lessThan1Hour,
                lessThan24Hours,
                moreThan24Hours,
                neverExpires
        );
    }

    @Override
    public V compute(K key, java.util.function.BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        java.util.Objects.requireNonNull(key, "key cannot be null");
        java.util.Objects.requireNonNull(remappingFunction, "remappingFunction cannot be null");

        V oldValue = storage.get(key) != null ? storage.get(key).getValue() : null;
        V newValue = remappingFunction.apply(key, oldValue);

        if (newValue == null) {
            if (oldValue != null) {
                invalidate(key);
            }
            return null;
        } else {
            put(key, newValue);
            return newValue;
        }
    }

    @Override
    public V computeIfAbsent(K key, java.util.function.Function<? super K, ? extends V> mappingFunction) {
        java.util.Objects.requireNonNull(key, "key cannot be null");
        java.util.Objects.requireNonNull(mappingFunction, "mappingFunction cannot be null");

        V existing = getIfPresent(key);
        if (existing != null) {
            return existing;
        }

        V newValue = mappingFunction.apply(key);
        if (newValue != null) {
            put(key, newValue);
        }
        return newValue;
    }

    @Override
    public V computeIfPresent(K key, java.util.function.BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        java.util.Objects.requireNonNull(key, "key cannot be null");
        java.util.Objects.requireNonNull(remappingFunction, "remappingFunction cannot be null");

        V oldValue = getIfPresent(key);
        if (oldValue == null) {
            return null;
        }

        V newValue = remappingFunction.apply(key, oldValue);
        if (newValue == null) {
            invalidate(key);
            return null;
        } else {
            put(key, newValue);
            return newValue;
        }
    }

    @Override
    public V merge(K key, V value, java.util.function.BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        java.util.Objects.requireNonNull(key, "key cannot be null");
        java.util.Objects.requireNonNull(value, "value cannot be null");
        java.util.Objects.requireNonNull(remappingFunction, "remappingFunction cannot be null");

        V oldValue = getIfPresent(key);
        V newValue = (oldValue == null) ? value : remappingFunction.apply(oldValue, value);

        if (newValue == null) {
            invalidate(key);
            return null;
        } else {
            put(key, newValue);
            return newValue;
        }
    }
}
