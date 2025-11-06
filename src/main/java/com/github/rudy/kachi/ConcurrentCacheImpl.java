package com.github.rudy.kachi;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * High-performance concurrent cache implementation with TTL, lazy loading, multiple eviction policies,
 * removal listeners, and write-priority semantics.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
class ConcurrentCacheImpl<K, V> implements LoadingCache<K, V> {
    private final ConcurrentHashMap<K, CacheEntry<V>> storage;
    private final CacheLoader<? super K, V> loader;
    private final long maximumSize;
    private final long expireAfterWriteNanos;
    private final long expireAfterAccessNanos;
    private final boolean recordStats;
    private final EvictionPolicy evictionPolicy;
    private final RemovalListener<? super K, ? super V> removalListener;

    // Statistics
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong loadSuccessCount = new AtomicLong(0);
    private final AtomicLong loadFailureCount = new AtomicLong(0);
    private final AtomicLong totalLoadTime = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);

    // Eviction tracking - different for each policy
    private final ConcurrentLinkedDeque<K> accessOrder = new ConcurrentLinkedDeque<>(); // For LRU and FIFO
    private final ConcurrentHashMap<K, ReentrantReadWriteLock> keyLocks = new ConcurrentHashMap<>();

    // For loading cache - prevent duplicate loads
    private final ConcurrentHashMap<K, CompletableFuture<V>> loadingFutures = new ConcurrentHashMap<>();

    // Scheduled cleanup for TTL expiration
    private final ScheduledExecutorService cleanupScheduler;
    private final ScheduledFuture<?> cleanupTask;

    // Read timeout for write-priority
    private static final long READ_TIMEOUT_MS = 1000;

    /**
     * Constructor for non-loading cache.
     */
    ConcurrentCacheImpl(CacheBuilder<?, ?> builder) {
        this(builder, null);
    }

    /**
     * Constructor for loading cache.
     */
    @SuppressWarnings("unchecked")
    ConcurrentCacheImpl(CacheBuilder<?, ?> builder, CacheLoader<? super K, V> loader) {
        this.storage = new ConcurrentHashMap<>(builder.getInitialCapacity(), 0.75f, builder.getConcurrencyLevel());
        this.loader = loader;
        this.maximumSize = builder.getMaximumSize();
        this.expireAfterWriteNanos = builder.getExpireAfterWriteNanos();
        this.expireAfterAccessNanos = builder.getExpireAfterAccessNanos();
        this.recordStats = builder.isRecordingStats();
        this.evictionPolicy = builder.getEvictionPolicy();
        this.removalListener = (RemovalListener<? super K, ? super V>) builder.getRemovalListener();

        // Start scheduled TTL cleanup task (runs every minute)
        if (expireAfterWriteNanos > 0 || expireAfterAccessNanos > 0) {
            this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "kachi-cache-cleanup");
                t.setDaemon(true);
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
                        accessOrder.remove(key);
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
            for (K key : keysToLoad) {
                try {
                    V value = get(key);
                    result.put(key, value);
                } catch (Exception ex) {
                    // Skip this key on error
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

        CompletableFuture.runAsync(() -> {
            try {
                V newValue = loader.load(key);
                if (newValue != null) {
                    put(key, newValue);
                }
            } catch (Exception e) {
                // Log and swallow as per contract
            }
        });
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
        long ttl = getTtlForEntry();
        CacheEntry<V> newEntry = new CacheEntry<>(value, ttl);

        CacheEntry<V> oldEntry = storage.put(key, newEntry);
        if (oldEntry != null) {
            fireRemovalEvent(key, oldEntry.getValue(), replacementCause);
        }

        updateAccessTracking(key);
        evictIfNecessary();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        Objects.requireNonNull(map, "map cannot be null");
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
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
                accessOrder.remove(key);
                fireRemovalEvent(key, removed.getValue(), RemovalCause.EXPLICIT);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void invalidateAll() {
        // Take snapshot to avoid concurrent modification
        Set<K> keys = new HashSet<>(storage.keySet());
        for (K key : keys) {
            invalidate(key);
        }
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
                    accessOrder.remove(key);
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

    private long getTtlForEntry() {
        if (expireAfterWriteNanos > 0) {
            return expireAfterWriteNanos;
        }
        return 0;
    }

    private void updateAccessTracking(K key) {
        if (maximumSize <= 0) {
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
        }
    }

    private void evictIfNecessary() {
        if (maximumSize <= 0) {
            return;
        }

        while (storage.size() > maximumSize) {
            K keyToEvict = selectKeyToEvict();
            if (keyToEvict == null) {
                break;
            }

            ReentrantReadWriteLock lock = getOrCreateLock(keyToEvict);
            if (lock.writeLock().tryLock()) {
                try {
                    CacheEntry<V> removed = storage.remove(keyToEvict);
                    if (removed != null) {
                        accessOrder.remove(keyToEvict);
                        fireRemovalEvent(keyToEvict, removed.getValue(), RemovalCause.SIZE);
                        if (recordStats) evictionCount.incrementAndGet();
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }
        }
    }

    private K selectKeyToEvict() {
        switch (evictionPolicy) {
            case LRU:
            case FIFO:
                return accessOrder.poll();
            case LFU:
                return selectLFUCandidate();
            default:
                return accessOrder.poll();
        }
    }

    private K selectLFUCandidate() {
        K candidate = null;
        long minCount = Long.MAX_VALUE;

        for (Map.Entry<K, CacheEntry<V>> entry : storage.entrySet()) {
            long count = entry.getValue().getAccessCount();
            if (count < minCount) {
                minCount = count;
                candidate = entry.getKey();
            }
        }

        return candidate;
    }

    private void fireRemovalEvent(K key, V value, RemovalCause cause) {
        if (removalListener != null) {
            try {
                removalListener.onRemoval(key, value, cause);
            } catch (Exception e) {
                // Log and swallow exceptions from listener
                System.err.println("RemovalListener threw exception: " + e.getMessage());
            }
        }
    }

    /**
     * Shutdown the cleanup scheduler when cache is no longer needed.
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
        }
    }

}
