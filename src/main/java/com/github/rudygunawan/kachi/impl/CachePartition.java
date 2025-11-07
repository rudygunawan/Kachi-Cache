package com.github.rudygunawan.kachi.impl;

import com.github.rudygunawan.kachi.model.CacheEntry;
import com.github.rudygunawan.kachi.policy.EvictionPolicy;
import com.github.rudygunawan.kachi.policy.FrequencySketch;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A partition (shard) of the cache that holds a subset of entries.
 *
 * <p>Cache partitioning improves concurrency by distributing entries across
 * multiple independent storage segments, each with its own locks. This reduces
 * lock contention and allows operations on different partitions to proceed in parallel.
 *
 * <p><b>Performance Impact:</b>
 * <ul>
 *   <li>Single-threaded: ~5% overhead (partition routing)</li>
 *   <li>Multi-threaded (4+ threads): 2-3x throughput improvement</li>
 *   <li>High contention (16+ threads): 3-5x throughput improvement</li>
 * </ul>
 *
 * <p><b>Architecture:</b>
 * <pre>
 * Cache (N partitions)
 *   ├─ Partition 0 (keys: hash % N == 0)
 *   │  ├─ storage: ConcurrentHashMap
 *   │  ├─ locks: per-key ReentrantReadWriteLock
 *   │  └─ weight: AtomicLong
 *   ├─ Partition 1 (keys: hash % N == 1)
 *   └─ ...
 * </pre>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
class CachePartition<K, V> {
    private final int partitionIndex;
    private final ConcurrentHashMap<K, CacheEntry<V>> storage;
    private final ConcurrentHashMap<K, ReentrantReadWriteLock> keyLocks;
    private final AtomicLong currentWeight;
    private final EvictionPolicy evictionPolicy;

    // Eviction tracking structures
    private final ConcurrentLinkedDeque<K> accessOrder; // For LRU/FIFO
    private final ConcurrentLinkedDeque<K> windowQueue;      // For TinyLFU
    private final ConcurrentLinkedDeque<K> probationQueue;   // For TinyLFU
    private final ConcurrentLinkedDeque<K> protectedQueue;   // For TinyLFU

    // Partition-specific limits (fraction of total cache limit)
    private final long maxSizePerPartition;
    private final long maxWeightPerPartition;

    /**
     * Creates a new cache partition.
     *
     * @param partitionIndex the index of this partition (0-based)
     * @param initialCapacity initial capacity for this partition's storage
     * @param maxSize maximum number of entries for this partition (0 = unlimited)
     * @param maxWeight maximum weight for this partition (0 = unlimited)
     * @param evictionPolicy the eviction policy to use
     * @param useTinyLFU whether to initialize TinyLFU structures
     */
    public CachePartition(int partitionIndex, int initialCapacity, long maxSize, long maxWeight,
                          EvictionPolicy evictionPolicy, boolean useTinyLFU) {
        this.partitionIndex = partitionIndex;
        this.storage = new ConcurrentHashMap<>(initialCapacity);
        this.keyLocks = new ConcurrentHashMap<>();
        this.currentWeight = new AtomicLong(0);
        this.evictionPolicy = evictionPolicy;
        this.maxSizePerPartition = maxSize;
        this.maxWeightPerPartition = maxWeight;

        // Initialize eviction structures based on policy
        if (evictionPolicy == EvictionPolicy.LRU || evictionPolicy == EvictionPolicy.FIFO) {
            this.accessOrder = new ConcurrentLinkedDeque<>();
        } else {
            this.accessOrder = null;
        }

        // Initialize TinyLFU structures if needed
        if (useTinyLFU) {
            this.windowQueue = new ConcurrentLinkedDeque<>();
            this.probationQueue = new ConcurrentLinkedDeque<>();
            this.protectedQueue = new ConcurrentLinkedDeque<>();
        } else {
            this.windowQueue = null;
            this.probationQueue = null;
            this.protectedQueue = null;
        }
    }

    /**
     * Returns the storage map for this partition.
     */
    public ConcurrentHashMap<K, CacheEntry<V>> getStorage() {
        return storage;
    }

    /**
     * Returns the key locks map for this partition.
     */
    public ConcurrentHashMap<K, ReentrantReadWriteLock> getKeyLocks() {
        return keyLocks;
    }

    /**
     * Returns the current weight of this partition.
     */
    public AtomicLong getCurrentWeight() {
        return currentWeight;
    }

    /**
     * Returns the access order queue (for LRU/FIFO).
     */
    public ConcurrentLinkedDeque<K> getAccessOrder() {
        return accessOrder;
    }

    /**
     * Returns the window queue (for TinyLFU).
     */
    public ConcurrentLinkedDeque<K> getWindowQueue() {
        return windowQueue;
    }

    /**
     * Returns the probation queue (for TinyLFU).
     */
    public ConcurrentLinkedDeque<K> getProbationQueue() {
        return probationQueue;
    }

    /**
     * Returns the protected queue (for TinyLFU).
     */
    public ConcurrentLinkedDeque<K> getProtectedQueue() {
        return protectedQueue;
    }

    /**
     * Returns the maximum size for this partition.
     */
    public long getMaxSize() {
        return maxSizePerPartition;
    }

    /**
     * Returns the maximum weight for this partition.
     */
    public long getMaxWeight() {
        return maxWeightPerPartition;
    }

    /**
     * Returns the current size of this partition.
     */
    public int size() {
        return storage.size();
    }

    /**
     * Returns the partition index.
     */
    public int getPartitionIndex() {
        return partitionIndex;
    }

    /**
     * Gets or creates a lock for the given key.
     */
    public ReentrantReadWriteLock getOrCreateLock(K key) {
        return keyLocks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
    }

    /**
     * Checks if this partition has exceeded its size or weight limits.
     */
    public boolean needsEviction() {
        return (maxSizePerPartition > 0 && storage.size() > maxSizePerPartition) ||
               (maxWeightPerPartition > 0 && currentWeight.get() > maxWeightPerPartition);
    }

    @Override
    public String toString() {
        return "CachePartition{" +
                "index=" + partitionIndex +
                ", size=" + storage.size() +
                ", weight=" + currentWeight.get() +
                ", maxSize=" + maxSizePerPartition +
                ", maxWeight=" + maxWeightPerPartition +
                '}';
    }
}
