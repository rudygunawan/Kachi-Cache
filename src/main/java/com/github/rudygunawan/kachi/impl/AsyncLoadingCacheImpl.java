package com.github.rudygunawan.kachi.impl;

import com.github.rudygunawan.kachi.api.AsyncCacheLoader;
import com.github.rudygunawan.kachi.api.AsyncLoadingCache;
import com.github.rudygunawan.kachi.api.CacheLoader;
import com.github.rudygunawan.kachi.api.LoadingCache;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Asynchronous wrapper around a synchronous {@link LoadingCache} implementation.
 *
 * <p>This implementation delegates all operations to the underlying synchronous loading cache
 * and wraps the results in {@link CompletableFuture} instances for non-blocking access.
 *
 * <p><b>Performance considerations:</b>
 * <ul>
 *   <li>Uses virtual threads (JDK 21) for async operations by default</li>
 *   <li>All cache operations are thread-safe</li>
 *   <li>Parallel loading for bulk operations</li>
 *   <li>Custom executor can be provided for async operations</li>
 * </ul>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 * @since 1.0.0
 */
public class AsyncLoadingCacheImpl<K, V> extends AsyncCacheImpl<K, V> implements AsyncLoadingCache<K, V> {

    private static final Logger LOGGER = Logger.getLogger("com.github.rudygunawan.kachi.AsyncCache");

    private final LoadingCache<K, V> loadingCache;
    private final AsyncCacheLoader<K, V> asyncLoader;
    private final Executor executor;

    /**
     * Creates an async loading cache wrapper with a default virtual thread executor.
     *
     * @param loadingCache the synchronous loading cache to wrap
     * @param asyncLoader the async loader for computing values
     */
    public AsyncLoadingCacheImpl(LoadingCache<K, V> loadingCache, AsyncCacheLoader<K, V> asyncLoader) {
        this(loadingCache, asyncLoader, createDefaultExecutor());
    }

    /**
     * Creates an async loading cache wrapper with a custom executor.
     *
     * @param loadingCache the synchronous loading cache to wrap
     * @param asyncLoader the async loader for computing values
     * @param executor the executor to use for async operations
     */
    public AsyncLoadingCacheImpl(LoadingCache<K, V> loadingCache, AsyncCacheLoader<K, V> asyncLoader,
                                  Executor executor) {
        super(loadingCache, executor);
        this.loadingCache = Objects.requireNonNull(loadingCache, "loadingCache cannot be null");
        this.asyncLoader = Objects.requireNonNull(asyncLoader, "asyncLoader cannot be null");
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
    }

    @Override
    public CompletableFuture<V> get(K key) {
        Objects.requireNonNull(key, "key cannot be null");

        // Check if already present
        V existing = loadingCache.getIfPresent(key);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }

        // Load asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadingCache.get(key);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load value for key: " + key, e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Map<K, V>> getAll(Iterable<? extends K> keys) {
        Objects.requireNonNull(keys, "keys cannot be null");

        // Convert to list and deduplicate
        List<K> keyList = new ArrayList<>();
        Set<K> seen = new HashSet<>();
        for (K key : keys) {
            if (key == null) {
                throw new NullPointerException("keys cannot contain null elements");
            }
            if (seen.add(key)) {
                keyList.add(key);
            }
        }

        if (keyList.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        // Load all asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadingCache.getAll(keyList);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load values", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> refresh(K key) {
        Objects.requireNonNull(key, "key cannot be null");

        return CompletableFuture.runAsync(() -> {
            try {
                V oldValue = loadingCache.getIfPresent(key);

                // Load new value asynchronously
                CompletableFuture<V> newValueFuture;
                if (oldValue != null) {
                    // Use reload if value exists
                    newValueFuture = asyncLoader.asyncReload(key, oldValue, executor);
                } else {
                    // Use load if no existing value
                    newValueFuture = asyncLoader.asyncLoad(key, executor);
                }

                // Wait for new value and put it in cache
                newValueFuture.thenAcceptAsync(newValue -> {
                    if (newValue != null) {
                        loadingCache.put(key, newValue);
                    } else {
                        loadingCache.invalidate(key);
                    }
                }, executor).exceptionally(throwable -> {
                    // Log and swallow refresh failures
                    LOGGER.log(Level.WARNING, "Exception thrown during refresh for key: " + key, throwable);
                    return null;
                }).join();

            } catch (Exception e) {
                // Log and swallow refresh failures
                LOGGER.log(Level.WARNING, "Exception thrown during refresh for key: " + key, e);
            }
        }, executor);
    }

    @Override
    public LoadingCache<K, V> synchronous() {
        return loadingCache;
    }

    /**
     * Creates a default executor using virtual threads (JDK 21).
     *
     * @return a virtual thread executor
     */
    public static Executor createDefaultExecutor() {
        return task -> Thread.ofVirtual()
                .name("kachi-async-loading-cache-", 0)
                .start(task);
    }

    /**
     * Creates a synchronous CacheLoader wrapper around an AsyncCacheLoader.
     *
     * @param asyncLoader the async loader to wrap
     * @param executor the executor to use for async operations
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return a synchronous cache loader
     */
    public static <K, V> CacheLoader<K, V> toSyncLoader(AsyncCacheLoader<K, V> asyncLoader, Executor executor) {
        return new CacheLoader<K, V>() {
            @Override
            public V load(K key) throws Exception {
                return asyncLoader.asyncLoad(key, executor).join();
            }

            @Override
            public Map<K, V> loadAll(Iterable<? extends K> keys) throws Exception {
                return asyncLoader.asyncLoadAll(keys, executor).join();
            }

            @Override
            public V reload(K key, V oldValue) throws Exception {
                return asyncLoader.asyncReload(key, oldValue, executor).join();
            }
        };
    }
}
