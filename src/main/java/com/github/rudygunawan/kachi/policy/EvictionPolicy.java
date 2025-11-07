package com.github.rudygunawan.kachi.policy;

/**
 * Eviction policy for determining which entries to remove when the cache reaches its maximum size.
 *
 * <p>Available policies:
 * <ul>
 *   <li>{@link #LRU} - Least Recently Used
 *   <li>{@link #LFU} - Least Frequently Used
 *   <li>{@link #FIFO} - First In First Out
 *   <li>{@link #WINDOW_TINY_LFU} - Window TinyLFU (Caffeine's advanced algorithm)
 * </ul>
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
    FIFO,

    /**
     * Window TinyLFU - Caffeine's advanced eviction policy that combines:
     * <ul>
     *   <li>Window: Small admission LRU (1%) for new entries (captures recency bursts)</li>
     *   <li>TinyLFU: Probabilistic frequency sketch for estimating historical access</li>
     *   <li>Segmented LRU: Main cache split into Protected (80%) and Probation (20%)</li>
     * </ul>
     *
     * <p>This policy provides near-optimal hit rates with O(1) time complexity.
     * It's particularly effective for:
     * <ul>
     *   <li>Workloads with both recency and frequency signals</li>
     *   <li>Scan-resistant caching (one-time accesses don't pollute cache)</li>
     *   <li>Adapting to changing access patterns</li>
     * </ul>
     *
     * <p><b>Performance:</b> Generally 10-30% better hit rates than LRU for most workloads.
     *
     * @see FrequencySketch
     */
    WINDOW_TINY_LFU
}
