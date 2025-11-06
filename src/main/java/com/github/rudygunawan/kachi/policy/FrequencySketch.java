package com.github.rudygunawan.kachi.policy;

import java.util.Random;

/**
 * A probabilistic data structure for estimating the frequency of elements using Count-Min Sketch.
 * This is a space-efficient way to track access frequencies without storing every key.
 *
 * <p>The Count-Min Sketch uses multiple hash functions and maintains a 2D array of counters.
 * When an element is accessed, multiple counters are incremented. To estimate frequency,
 * we return the minimum of those counter values.
 *
 * <p>This implementation is optimized for cache eviction policies, where we need to:
 * <ul>
 *   <li>Track access frequencies with minimal memory overhead
 *   <li>Quickly estimate which entries are more frequently accessed
 *   <li>Periodically reset counters to adapt to changing access patterns
 * </ul>
 *
 * <p>Space complexity: O(width × depth) where width and depth are small constants.
 * Time complexity: O(depth) for increment and estimate, typically O(4) for 4 hash functions.
 *
 * <p>Based on the Count-Min Sketch paper by Cormode and Muthukrishnan (2005) and
 * Caffeine's implementation by Ben Manes.
 *
 * @see <a href="https://github.com/ben-manes/caffeine">Caffeine Cache</a>
 */
public class FrequencySketch {
    // Number of counter arrays (depth)
    private static final int DEPTH = 4;

    // Seeds for hash functions
    private static final long[] SEEDS = {
            0xc3a5c85c97cb3127L,
            0xb492b66fbe98f273L,
            0x9ae16a3b2f90404fL,
            0xcbf29ce484222325L
    };

    // 2D array of counters: [depth][width]
    private final long[][] table;
    private final int width;
    private final int sizeMask;
    private long size;

    // Sample size for deciding when to reset (adaptive reset)
    private static final int RESET_SAMPLE_SIZE = 10;
    private final Random random;

    /**
     * Creates a new frequency sketch with the specified maximum size.
     * The sketch is sized to provide good accuracy for the given cache size.
     *
     * @param maximumSize the maximum number of entries the cache can hold
     */
    public FrequencySketch(long maximumSize) {
        // Width should be a power of 2 for efficient modulo via bitwise AND
        // Use about 10x the maximum size for good accuracy
        int width = ceilingPowerOfTwo((int) Math.min(maximumSize * 10, Integer.MAX_VALUE / DEPTH));
        this.width = width;
        this.sizeMask = width - 1;
        this.table = new long[DEPTH][width];
        this.random = new Random();
        this.size = 0;
    }

    /**
     * Records that an entry was accessed.
     *
     * @param key the key that was accessed
     */
    public void increment(Object key) {
        int hash = spread(key.hashCode());

        // Increment counter in each row
        for (int i = 0; i < DEPTH; i++) {
            int index = indexOf(hash, i);
            table[i][index] = Math.min(15, table[i][index] + 1); // Cap at 15 to fit in 4 bits
        }

        size++;

        // Adaptive reset: periodically halve all counters to forget old access patterns
        if (size % (width * 10) == 0) {
            reset();
        }
    }

    /**
     * Estimates the frequency of accesses for the given key.
     * Returns the minimum count across all hash functions (Count-Min Sketch property).
     *
     * @param key the key to estimate frequency for
     * @return the estimated access frequency (0-15)
     */
    public int frequency(Object key) {
        int hash = spread(key.hashCode());
        int min = Integer.MAX_VALUE;

        // Find minimum across all rows
        for (int i = 0; i < DEPTH; i++) {
            int index = indexOf(hash, i);
            min = (int) Math.min(min, table[i][index]);
        }

        return min;
    }

    /**
     * Resets the sketch by halving all counters.
     * This helps the sketch adapt to changing access patterns over time.
     */
    private void reset() {
        for (int i = 0; i < DEPTH; i++) {
            for (int j = 0; j < width; j++) {
                table[i][j] >>>= 1; // Divide by 2 (right shift)
            }
        }
        size = size >>> 1;
    }

    /**
     * Clears all counters in the sketch.
     */
    public void clear() {
        for (int i = 0; i < DEPTH; i++) {
            for (int j = 0; j < width; j++) {
                table[i][j] = 0;
            }
        }
        size = 0;
    }

    /**
     * Returns the total number of increments recorded.
     */
    public long size() {
        return size;
    }

    /**
     * Computes the table index for a given hash and row.
     */
    private int indexOf(int hash, int row) {
        long seed = SEEDS[row];
        long h = (hash ^ seed) * 0x9E3779B97F4A7C15L; // Mix the hash
        h = (h ^ (h >>> 33)) >>> 32; // High 32 bits
        return ((int) h) & sizeMask;
    }

    /**
     * Spreads the hash code to reduce collisions.
     * Based on MurmurHash3's finalizer.
     */
    private int spread(int hash) {
        hash ^= hash >>> 16;
        hash *= 0x85ebca6b;
        hash ^= hash >>> 13;
        hash *= 0xc2b2ae35;
        hash ^= hash >>> 16;
        return hash;
    }

    /**
     * Returns the smallest power of two greater than or equal to the given value.
     */
    private static int ceilingPowerOfTwo(int x) {
        // Handle edge cases
        if (x <= 1) return 1;
        if (x >= (1 << 30)) return 1 << 30;

        // Round up to next power of 2
        x--;
        x |= x >>> 1;
        x |= x >>> 2;
        x |= x >>> 4;
        x |= x >>> 8;
        x |= x >>> 16;
        return x + 1;
    }

    /**
     * Returns a string representation of the sketch's statistics.
     */
    @Override
    public String toString() {
        return String.format("FrequencySketch{width=%d, depth=%d, size=%d}",
                width, DEPTH, size);
    }
}
