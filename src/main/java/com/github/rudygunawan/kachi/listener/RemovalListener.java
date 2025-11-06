package com.github.rudygunawan.kachi.listener;

import com.github.rudygunawan.kachi.policy.RemovalCause;

/**
 * A listener that receives notification when an entry is removed from a cache.
 *
 * <p>Implementations should be thread-safe and should not perform expensive operations
 * as they are called synchronously during cache operations.
 *
 * <p>Usage example:
 * <pre>{@code
 * RemovalListener<Key, Value> listener = new RemovalListener<Key, Value>() {
 *   public void onRemoval(Key key, Value value, RemovalCause cause) {
 *     System.out.println("Removed " + key + " because " + cause);
 *   }
 * };
 *
 * Cache<Key, Value> cache = CacheBuilder.newBuilder()
 *     .removalListener(listener)
 *     .build();
 * }</pre>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
@FunctionalInterface
public interface RemovalListener<K, V> {

    /**
     * Notifies the listener that a removal occurred at some point in the past.
     *
     * <p>This method is called synchronously during cache operations, so implementations
     * should be fast and non-blocking. For expensive operations, consider using an
     * asynchronous executor.
     *
     * @param key the key of the removed entry
     * @param value the value of the removed entry
     * @param cause the reason for the removal
     */
    void onRemoval(K key, V value, RemovalCause cause);
}
