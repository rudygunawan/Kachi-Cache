package com.github.rudygunawan.kachi.reference;

import com.github.rudygunawan.kachi.policy.Strength;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * A reference wrapper for cache keys that supports strong, weak, and soft references.
 *
 * <p>This class provides a unified interface for handling keys with different reference strengths,
 * allowing the cache to integrate with Java's garbage collection system.
 *
 * <p><b>Reference Strengths:</b>
 * <ul>
 *   <li>{@link Strength#STRONG} - Normal reference, never collected by GC</li>
 *   <li>{@link Strength#WEAK} - Collected when no strong references exist</li>
 *   <li>{@link Strength#SOFT} - Collected under memory pressure (Not applicable to keys, treated as WEAK)</li>
 * </ul>
 *
 * <p><b>Note:</b> Soft references are not recommended for keys as they may cause memory leaks.
 * When SOFT is specified for keys, WEAK is used instead.
 *
 * @param <K> the type of the key
 * @since 0.2.0
 */
public abstract class KeyReference<K> {

    private final int hashCode;

    protected KeyReference(K key) {
        // Cache the hash code since the key might be GC'd
        this.hashCode = Objects.hashCode(key);
    }

    /**
     * Creates a key reference with the specified strength.
     *
     * @param key the key to wrap
     * @param strength the reference strength
     * @param queue the reference queue for GC notifications (null for STRONG)
     * @param <K> the type of the key
     * @return a key reference
     */
    public static <K> KeyReference<K> create(K key, Strength strength, ReferenceQueue<K> queue) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(strength, "strength cannot be null");

        return switch (strength) {
            case STRONG -> new StrongKeyReference<>(key);
            case WEAK, SOFT -> new WeakKeyReference<>(key, queue); // SOFT not supported for keys
        };
    }

    /**
     * Returns the referenced key, or null if it has been garbage collected.
     *
     * @return the key, or null if cleared
     */
    public abstract K get();

    /**
     * Returns the reference strength.
     *
     * @return the strength
     */
    public abstract Strength getStrength();

    /**
     * Checks if the reference has been cleared by GC.
     *
     * @return true if cleared, false otherwise
     */
    public boolean isCleared() {
        return get() == null;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof KeyReference<?> other)) return false;

        K thisKey = get();
        Object otherKey = other.get();

        // If either key is null (GC'd), they're not equal
        if (thisKey == null || otherKey == null) {
            return false;
        }

        return Objects.equals(thisKey, otherKey);
    }

    /**
     * Strong reference implementation (default).
     */
    private static class StrongKeyReference<K> extends KeyReference<K> {
        private final K key;

        StrongKeyReference(K key) {
            super(key);
            this.key = key;
        }

        @Override
        public K get() {
            return key;
        }

        @Override
        public Strength getStrength() {
            return Strength.STRONG;
        }
    }

    /**
     * Weak reference implementation.
     */
    private static class WeakKeyReference<K> extends KeyReference<K> {
        private final WeakReference<K> ref;

        WeakKeyReference(K key, ReferenceQueue<K> queue) {
            super(key);
            this.ref = queue != null ? new WeakReference<>(key, queue) : new WeakReference<>(key);
        }

        @Override
        public K get() {
            return ref.get();
        }

        @Override
        public Strength getStrength() {
            return Strength.WEAK;
        }
    }
}
