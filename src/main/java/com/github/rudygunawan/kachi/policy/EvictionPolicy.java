package com.github.rudygunawan.kachi.policy;

/**
 * Eviction policy for determining which entries to remove when the cache reaches its maximum size.
 */
public enum EvictionPolicy {
    /**
     * Least Recently Used (LRU) - evicts entries that haven't been accessed recently.
     * This is the default policy and works well for most use cases.
     */
    LRU,

    /**
     * Least Frequently Used (LFU) - evicts entries with the lowest access count.
     * Good for workloads where some keys are accessed much more frequently than others.
     */
    LFU,

    /**
     * First In First Out (FIFO) - evicts the oldest entries first, regardless of access patterns.
     * Simple policy that works well when newer entries are more valuable than older ones.
     */
    FIFO
}
