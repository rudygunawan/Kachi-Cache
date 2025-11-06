package com.github.rudy.kachi;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-performance concurrent cache implementation with TTL, lazy loading, and LRU eviction support.
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

    // Statistics
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong loadSuccessCount = new AtomicLong(0);
    private final AtomicLong loadFailureCount = new AtomicLong(0);
    private final AtomicLong totalLoadTime = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);

    // LRU tracking
    private final ConcurrentLinkedDeque<K> accessOrder = new ConcurrentLinkedDeque<>();
    private final ReentrantLock evictionLock = new ReentrantLock();

    // For loading cache - prevent duplicate loads
    private final ConcurrentHashMap<K, CompletableFuture<V>> loadingFutures = new ConcurrentHashMap<>();

    /**
     * Constructor for non-loading cache.
     */
    ConcurrentCacheImpl(CacheBuilder<?, ?> builder) {
        this(builder, null);
    }

    /**
     * Constructor for loading cache.
     */
    ConcurrentCacheImpl(CacheBuilder<?, ?> builder, CacheLoader<? super K, V> loader) {
        this.storage = new ConcurrentHashMap<>(builder.getInitialCapacity(), 0.75f, builder.getConcurrencyLevel());
        this.loader = loader;
        this.maximumSize = builder.getMaximumSize();
        this.expireAfterWriteNanos = builder.getExpireAfterWriteNanos();
        this.expireAfterAccessNanos = builder.getExpireAfterAccessNanos();
        this.recordStats = builder.isRecordingStats();
    }

    @Override
    public V getIfPresent(K key) {
        Objects.requireNonNull(key, "key cannot be null");
        CacheEntry<V> entry = storage.get(key);

        if (entry == null) {
            if (recordStats) missCount.incrementAndGet();
            return null;
        }

        // Check if expired
        if (isExpired(entry)) {
            storage.remove(key, entry);
            accessOrder.remove(key);
            if (recordStats) {
                missCount.incrementAndGet();
                evictionCount.incrementAndGet();
            }
            return null;
        }

        // Update access time for expire-after-access
        if (expireAfterAccessNanos > 0) {
            entry.updateAccessTime();
        }

        // Update LRU order
        updateAccessOrder(key);

        if (recordStats) hitCount.incrementAndGet();
        return entry.getValue();
    }

    @Override
    public V get(K key, Callable<? extends V> loader) throws Exception {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(loader, "loader cannot be null");

        V value = getIfPresent(key);
        if (value != null) {
            return value;
        }

        // Load the value
        long startTime = System.nanoTime();
        try {
            value = loader.call();
            if (value == null) {
                throw new NullPointerException("loader returned null value");
            }
            put(key, value);
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
            // Another thread is already loading this key
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

        // Create a new future for this load operation
        CompletableFuture<V> newFuture = new CompletableFuture<>();
        CompletableFuture<V> existingFuture = loadingFutures.putIfAbsent(key, newFuture);

        if (existingFuture != null) {
            // Another thread beat us to it
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

        // We're responsible for loading
        long startTime = System.nanoTime();
        try {
            value = loader.load(key);
            if (value == null) {
                throw new NullPointerException("loader returned null value for key: " + key);
            }
            put(key, value);
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
        } finally {
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

        // Try bulk load if supported
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
            // Fall back to individual loads
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

        // Asynchronously reload the value
        CompletableFuture.runAsync(() -> {
            try {
                V newValue = loader.load(key);
                if (newValue != null) {
                    put(key, newValue);
                }
            } catch (Exception e) {
                // Log and swallow exception as per contract
            }
        });
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        long ttl = getTtlForEntry();
        CacheEntry<V> entry = new CacheEntry<>(value, ttl);
        storage.put(key, entry);
        updateAccessOrder(key);

        // Check if we need to evict
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
        if (storage.remove(key) != null) {
            accessOrder.remove(key);
        }
    }

    @Override
    public void invalidateAll() {
        storage.clear();
        accessOrder.clear();
    }

    @Override
    public long size() {
        cleanUp();
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
        // Remove expired entries
        long now = System.nanoTime();
        Iterator<Map.Entry<K, CacheEntry<V>>> iterator = storage.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, CacheEntry<V>> entry = iterator.next();
            if (isExpired(entry.getValue())) {
                iterator.remove();
                accessOrder.remove(entry.getKey());
                if (recordStats) evictionCount.incrementAndGet();
            }
        }
    }

    @Override
    public Map<K, V> asMap() {
        cleanUp();
        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, CacheEntry<V>> entry : storage.entrySet()) {
            if (!isExpired(entry.getValue())) {
                result.put(entry.getKey(), entry.getValue().getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    // Helper methods

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
        return 0; // No expiration
    }

    private void updateAccessOrder(K key) {
        if (maximumSize <= 0) {
            return;
        }
        // Remove and re-add to move to end (most recently used)
        accessOrder.remove(key);
        accessOrder.offer(key);
    }

    private void evictIfNecessary() {
        if (maximumSize <= 0) {
            return;
        }

        while (storage.size() > maximumSize) {
            evictionLock.lock();
            try {
                // Double-check after acquiring lock
                if (storage.size() <= maximumSize) {
                    break;
                }

                // Evict least recently used
                K keyToEvict = accessOrder.poll();
                if (keyToEvict != null && storage.remove(keyToEvict) != null) {
                    if (recordStats) evictionCount.incrementAndGet();
                }
            } finally {
                evictionLock.unlock();
            }
        }
    }
}
