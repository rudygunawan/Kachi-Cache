package com.github.rudygunawan.kachi.reference;

import com.github.rudygunawan.kachi.policy.Strength;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * A reference wrapper for cache values that supports strong, weak, and soft references.
 *
 * <p>This class provides a unified interface for handling values with different reference strengths,
 * allowing the cache to integrate with Java's garbage collection system.
 *
 * <p><b>Reference Strengths:</b>
 * <ul>
 *   <li>{@link Strength#STRONG} - Normal reference, never collected by GC</li>
 *   <li>{@link Strength#WEAK} - Collected when no strong references exist</li>
 *   <li>{@link Strength#SOFT} - Collected under memory pressure (recommended for values)</li>
 * </ul>
 *
 * <p><b>Example usage:</b>
 * <pre>{@code
 * ReferenceQueue<User> queue = new ReferenceQueue<>();
 * ValueReference<User> ref = ValueReference.create(user, Strength.SOFT, queue);
 *
 * User value = ref.get(); // May return null if GC'd
 * if (value != null) {
 *     // Use value
 * }
 * }</pre>
 *
 * @param <V> the type of the value
 * @since 0.2.0
 */
public abstract class ValueReference<V> {

    protected ValueReference() {
    }

    /**
     * Creates a value reference with the specified strength.
     *
     * @param value the value to wrap
     * @param strength the reference strength
     * @param queue the reference queue for GC notifications (null for STRONG)
     * @param <V> the type of the value
     * @return a value reference
     */
    public static <V> ValueReference<V> create(V value, Strength strength, ReferenceQueue<V> queue) {
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(strength, "strength cannot be null");

        return switch (strength) {
            case STRONG -> new StrongValueReference<>(value);
            case WEAK -> new WeakValueReference<>(value, queue);
            case SOFT -> new SoftValueReference<>(value, queue);
        };
    }

    /**
     * Returns the referenced value, or null if it has been garbage collected.
     *
     * @return the value, or null if cleared
     */
    public abstract V get();

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

    /**
     * Strong reference implementation (default).
     */
    private static class StrongValueReference<V> extends ValueReference<V> {
        private final V value;

        StrongValueReference(V value) {
            this.value = value;
        }

        @Override
        public V get() {
            return value;
        }

        @Override
        public Strength getStrength() {
            return Strength.STRONG;
        }
    }

    /**
     * Weak reference implementation.
     */
    private static class WeakValueReference<V> extends ValueReference<V> {
        private final WeakReference<V> ref;

        WeakValueReference(V value, ReferenceQueue<V> queue) {
            this.ref = queue != null ? new WeakReference<>(value, queue) : new WeakReference<>(value);
        }

        @Override
        public V get() {
            return ref.get();
        }

        @Override
        public Strength getStrength() {
            return Strength.WEAK;
        }
    }

    /**
     * Soft reference implementation.
     */
    private static class SoftValueReference<V> extends ValueReference<V> {
        private final SoftReference<V> ref;

        SoftValueReference(V value, ReferenceQueue<V> queue) {
            this.ref = queue != null ? new SoftReference<>(value, queue) : new SoftReference<>(value);
        }

        @Override
        public V get() {
            return ref.get();
        }

        @Override
        public Strength getStrength() {
            return Strength.SOFT;
        }
    }
}
