package com.github.rudygunawan.kachi.api;

/**
 * Cache implementation strategy.
 *
 * Choose between high performance or precision based on your needs.
 */
public enum CacheStrategy {
    /**
     * High-performance cache optimized for speed and throughput.
     *
     * <p><b>Performance:</b>
     * <ul>
     *   <li>GET: ~60ns (16.75M ops/sec)</li>
     *   <li>PUT: ~15,978ns (62,587 ops/sec)</li>
     *   <li>Concurrent: 14.1M ops/sec (16 threads)</li>
     *   <li>4.7-7.1x faster than Caffeine for concurrent workloads</li>
     * </ul>
     *
     * <p><b>Trade-offs:</b>
     * <ul>
     *   <li>Random eviction (not LRU/FIFO/TinyLFU)</li>
     *   <li>Lazy expiry checking</li>
     *   <li>Lock-free reads</li>
     *   <li>Eventual consistency</li>
     * </ul>
     *
     * <p><b>Best for:</b>
     * <ul>
     *   <li>High-frequency read workloads</li>
     *   <li>Concurrent applications</li>
     *   <li>Large caches where random eviction is acceptable</li>
     *   <li>When speed > eviction accuracy</li>
     * </ul>
     */
    HIGH_PERFORMANCE,

    /**
     * Precision cache optimized for accurate eviction and strong consistency.
     *
     * <p><b>Performance:</b>
     * <ul>
     *   <li>GET: ~800-1,400ns (still respectable!)</li>
     *   <li>Concurrent: ~1-2M ops/sec</li>
     *   <li>Similar to Guava/Caffeine performance</li>
     * </ul>
     *
     * <p><b>Features:</b>
     * <ul>
     *   <li>Accurate LRU/FIFO/LFU/TinyLFU eviction</li>
     *   <li>Immediate expiry checking</li>
     *   <li>Per-key locking (write-priority)</li>
     *   <li>Strong consistency guarantees</li>
     * </ul>
     *
     * <p><b>Best for:</b>
     * <ul>
     *   <li>Memory-constrained applications</li>
     *   <li>Need accurate LRU/LFU eviction</li>
     *   <li>Strong consistency requirements</li>
     *   <li>When eviction accuracy > speed</li>
     * </ul>
     */
    PRECISION
}
