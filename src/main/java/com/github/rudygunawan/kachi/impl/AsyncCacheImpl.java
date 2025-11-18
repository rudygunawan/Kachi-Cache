package com.github.rudygunawan.kachi.impl;

import com.github.rudygunawan.kachi.api.AsyncCache;
import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.model.CacheStats;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Asynchronous wrapper around a synchronous {@link Cache} implementation.
 *
 * <p>This implementation delegates all operations to the underlying synchronous cache and wraps
 * the results in {@link CompletableFuture} instances for non-blocking access.
 *
 * <p><b>Performance considerations:</b>
 * <ul>
 *   <li>Uses virtual threads (JDK 21) for async operations by default</li>
 *   <li>All cache operations are thread-safe</li>
 *   <li>Custom executor can be provided for async operations</li>
 * </ul>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @since 1.0.0
 */
public class AsyncCacheImpl<K, V> implements AsyncCache<K, V> {

    private final Cache<K, V> cache;
    private final Executor executor;

    /**
     * Creates an async cache wrapper with a default virtual thread executor.
     *
     * @param cache the synchronous cache to wrap
     */
    public AsyncCacheImpl(Cache<K, V> cache) {
        this(cache, createDefaultExecutor());
    }

    /**
     * Creates an async cache wrapper with a custom executor.
     *
     * @param cache the synchronous cache to wrap
     * @param executor the executor to use for async operations
     */
    public AsyncCacheImpl(Cache<K, V> cache, Executor executor) {
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
    }

    @Override
    public CompletableFuture<V> get(K key, Function<? super K, ? extends CompletableFuture<V>> mappingFunction) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(mappingFunction, "mappingFunction cannot be null");

        // Check if already present
        V existing = cache.getIfPresent(key);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }

        // Compute asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Use the synchronous cache's get method with a callable
                return cache.get(key, () -> {
                    // Execute the mapping function and wait for result
                    CompletableFuture<V> future = mappingFunction.apply(key);
                    return future.join();
                });
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute value for key: " + key, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<V> getIfPresent(K key) {
        Objects.requireNonNull(key, "key cannot be null");
        return CompletableFuture.completedFuture(cache.getIfPresent(key));
    }

    @Override
    public CompletableFuture<Void> put(K key, V value) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        return CompletableFuture.runAsync(() -> cache.put(key, value), executor);
    }

    @Override
    public CompletableFuture<Map<K, V>> getAllPresent(Iterable<? extends K> keys) {
        Objects.requireNonNull(keys, "keys cannot be null");
        return CompletableFuture.supplyAsync(() -> cache.getAllPresent(keys), executor);
    }

    @Override
    public CompletableFuture<Void> putAll(Map<? extends K, ? extends V> map) {
        Objects.requireNonNull(map, "map cannot be null");
        return CompletableFuture.runAsync(() -> cache.putAll(map), executor);
    }

    @Override
    public CompletableFuture<Void> invalidate(K key) {
        Objects.requireNonNull(key, "key cannot be null");
        return CompletableFuture.runAsync(() -> cache.invalidate(key), executor);
    }

    @Override
    public CompletableFuture<Void> invalidateAll(Iterable<? extends K> keys) {
        Objects.requireNonNull(keys, "keys cannot be null");
        return CompletableFuture.runAsync(() -> cache.invalidateAll(keys), executor);
    }

    @Override
    public CompletableFuture<Void> invalidateAll() {
        return CompletableFuture.runAsync(cache::invalidateAll, executor);
    }

    @Override
    public long estimatedSize() {
        return cache.size();
    }

    @Override
    public CacheStats stats() {
        return cache.stats();
    }

    @Override
    public ConcurrentMap<K, V> asMap() {
        return cache.asMap();
    }

    @Override
    public Cache<K, V> synchronous() {
        return cache;
    }

    /**
     * Creates a default executor using virtual threads (JDK 21).
     *
     * @return a virtual thread executor
     */
    private static Executor createDefaultExecutor() {
        return task -> Thread.ofVirtual()
                .name("kachi-async-cache-", 0)
                .start(task);
    }
}
