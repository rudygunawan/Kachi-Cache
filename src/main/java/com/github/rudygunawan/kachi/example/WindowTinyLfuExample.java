package com.github.rudygunawan.kachi.example;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.impl.ConcurrentCacheImpl;
import com.github.rudygunawan.kachi.policy.EvictionPolicy;

import java.util.Random;

/**
 * Example demonstrating the Window TinyLFU eviction policy's advantages over LRU.
 *
 * <p>Window TinyLFU combines recency and frequency signals to achieve near-optimal hit rates.
 * This example shows its superiority in three scenarios:
 * <ul>
 *   <li>Scan resistance: One-time accesses don't pollute the cache</li>
 *   <li>Frequency-based workloads: Frequently accessed items are protected</li>
 *   <li>Mixed workloads: Adapts to both recency and frequency patterns</li>
 * </ul>
 */
public class WindowTinyLfuExample {

    public static void main(String[] args) {
        System.out.println("=== Window TinyLFU vs LRU Performance Comparison ===\n");

        // Test 1: Scan Resistance
        System.out.println("=== Test 1: Scan Resistance ===");
        System.out.println("Scenario: A cache with frequently accessed 'hot' items, plus occasional large scans.");
        System.out.println("LRU Problem: Scans evict hot items, causing cache pollution.");
        System.out.println("TinyLFU Solution: Scans are blocked from entering, hot items stay protected.\n");

        testScanResistance();

        System.out.println("\n" + "=".repeat(80) + "\n");

        // Test 2: Frequency-based Workload
        System.out.println("=== Test 2: Frequency-Based Workload ===");
        System.out.println("Scenario: Some items are accessed much more frequently than others.");
        System.out.println("LRU Problem: Recent but infrequent items can evict old but frequent items.");
        System.out.println("TinyLFU Solution: Frequency tracking protects hot items.\n");

        testFrequencyBased();

        System.out.println("\n" + "=".repeat(80) + "\n");

        // Test 3: Mixed Workload
        System.out.println("=== Test 3: Mixed Workload (Most Realistic) ===");
        System.out.println("Scenario: Combination of hot items, warm items, and one-time accesses.");
        System.out.println("TinyLFU Solution: Adapts to both recency and frequency patterns.\n");

        testMixedWorkload();

        System.out.println("\n=== Conclusion ===");
        System.out.println("Window TinyLFU provides 10-30% better hit rates than LRU for most workloads.");
        System.out.println("It's particularly effective for:");
        System.out.println("  - Workloads with scan patterns (large sequential accesses)");
        System.out.println("  - Workloads with skewed access patterns (some items more popular)");
        System.out.println("  - Production systems with mixed access patterns");
    }

    /**
     * Test scan resistance: Frequent items + large scans.
     * Expected: TinyLFU should significantly outperform LRU.
     */
    private static void testScanResistance() {
        int cacheSize = 100;
        int numHotItems = 20;  // Frequently accessed items
        int scanSize = 500;     // Large scan that exceeds cache size

        // Create LRU cache
        Cache<Integer, String> lruCache = CacheBuilder.newBuilder()
                .maximumSize(cacheSize)
                .evictionPolicy(EvictionPolicy.LRU)
                .recordStats()
                .build();

        // Create Window TinyLFU cache
        Cache<Integer, String> tinyLfuCache = CacheBuilder.newBuilder()
                .maximumSize(cacheSize)
                .evictionPolicy(EvictionPolicy.WINDOW_TINY_LFU)
                .recordStats()
                .build();

        Random random = new Random(42);

        // Phase 1: Populate caches with hot items
        System.out.println("Phase 1: Populating with " + numHotItems + " hot items...");
        for (int i = 0; i < numHotItems; i++) {
            lruCache.put(i, "hot-" + i);
            tinyLfuCache.put(i, "hot-" + i);
        }

        // Phase 2: Access hot items repeatedly to establish them
        System.out.println("Phase 2: Establishing hot items with 1000 accesses...");
        for (int i = 0; i < 1000; i++) {
            int key = random.nextInt(numHotItems);
            lruCache.getIfPresent(key);
            tinyLfuCache.getIfPresent(key);
        }

        // Phase 3: Large scan (cache pollution attack)
        System.out.println("Phase 3: Performing large scan of " + scanSize + " items (scan attack)...");
        for (int i = 1000; i < 1000 + scanSize; i++) {
            lruCache.put(i, "scan-" + i);
            tinyLfuCache.put(i, "scan-" + i);
        }

        // Phase 4: Access hot items again and measure hit rate
        System.out.println("Phase 4: Accessing hot items again to measure hit rate...");
        long lruHitsBefore = ((ConcurrentCacheImpl<?, ?>) lruCache).hitCount();
        long tinyLfuHitsBefore = ((ConcurrentCacheImpl<?, ?>) tinyLfuCache).hitCount();

        for (int i = 0; i < numHotItems; i++) {
            lruCache.getIfPresent(i);
            tinyLfuCache.getIfPresent(i);
        }

        long lruHitsAfter = ((ConcurrentCacheImpl<?, ?>) lruCache).hitCount();
        long tinyLfuHitsAfter = ((ConcurrentCacheImpl<?, ?>) tinyLfuCache).hitCount();

        long lruHotItemHits = lruHitsAfter - lruHitsBefore;
        long tinyLfuHotItemHits = tinyLfuHitsAfter - tinyLfuHitsBefore;

        System.out.println("\nResults:");
        System.out.println("  LRU hot item hits: " + lruHotItemHits + "/" + numHotItems +
                " (" + String.format("%.1f", lruHotItemHits * 100.0 / numHotItems) + "%)");
        System.out.println("  TinyLFU hot item hits: " + tinyLfuHotItemHits + "/" + numHotItems +
                " (" + String.format("%.1f", tinyLfuHotItemHits * 100.0 / numHotItems) + "%)");
        System.out.println("  TinyLFU Advantage: +" + (tinyLfuHotItemHits - lruHotItemHits) + " hits");

        if (tinyLfuHotItemHits > lruHotItemHits) {
            System.out.println("  ✓ TinyLFU successfully resisted scan pollution!");
        }
    }

    /**
     * Test frequency-based workload: Skewed access pattern (Zipf distribution).
     * Expected: TinyLFU should outperform LRU.
     */
    private static void testFrequencyBased() {
        int cacheSize = 50;
        int workingSet = 200;
        int numAccesses = 5000;

        // Create LRU cache
        Cache<Integer, String> lruCache = CacheBuilder.newBuilder()
                .maximumSize(cacheSize)
                .evictionPolicy(EvictionPolicy.LRU)
                .recordStats()
                .build();

        // Create Window TinyLFU cache
        Cache<Integer, String> tinyLfuCache = CacheBuilder.newBuilder()
                .maximumSize(cacheSize)
                .evictionPolicy(EvictionPolicy.WINDOW_TINY_LFU)
                .recordStats()
                .build();

        Random random = new Random(42);

        System.out.println("Simulating Zipf distribution (80/20 rule)...");
        System.out.println("  80% of accesses go to 20% of items (hot items)");
        System.out.println("  20% of accesses go to remaining 80% of items");
        System.out.println("  Total accesses: " + numAccesses);

        for (int i = 0; i < numAccesses; i++) {
            int key;
            // 80% of the time, access hot items (first 20%)
            if (random.nextDouble() < 0.8) {
                key = random.nextInt(workingSet / 5); // 20% of items
            } else {
                key = random.nextInt(workingSet);
            }

            String value = "value-" + key;

            // Try to get, if miss then put
            if (lruCache.getIfPresent(key) == null) {
                lruCache.put(key, value);
            }
            if (tinyLfuCache.getIfPresent(key) == null) {
                tinyLfuCache.put(key, value);
            }
        }

        ConcurrentCacheImpl<?, ?> lruImpl = (ConcurrentCacheImpl<?, ?>) lruCache;
        ConcurrentCacheImpl<?, ?> tinyLfuImpl = (ConcurrentCacheImpl<?, ?>) tinyLfuCache;

        double lruHitRate = lruImpl.hitCount() * 100.0 / (lruImpl.hitCount() + lruImpl.missCount());
        double tinyLfuHitRate = tinyLfuImpl.hitCount() * 100.0 / (tinyLfuImpl.hitCount() + tinyLfuImpl.missCount());

        System.out.println("\nResults:");
        System.out.println("  LRU Hit Rate: " + String.format("%.2f", lruHitRate) + "%");
        System.out.println("  TinyLFU Hit Rate: " + String.format("%.2f", tinyLfuHitRate) + "%");
        System.out.println("  Improvement: +" + String.format("%.2f", tinyLfuHitRate - lruHitRate) + " percentage points");

        if (tinyLfuHitRate > lruHitRate) {
            System.out.println("  ✓ TinyLFU achieves better hit rate with frequency tracking!");
        }
    }

    /**
     * Test mixed workload: Hot items, warm items, cold items, and scans.
     * This is the most realistic scenario.
     */
    private static void testMixedWorkload() {
        int cacheSize = 100;
        int numHotItems = 20;    // 20% - accessed 60% of the time
        int numWarmItems = 30;   // 30% - accessed 30% of the time
        int numColdItems = 100;  // 50% - accessed 10% of the time
        int numAccesses = 10000;

        // Create LRU cache
        Cache<Integer, String> lruCache = CacheBuilder.newBuilder()
                .maximumSize(cacheSize)
                .evictionPolicy(EvictionPolicy.LRU)
                .recordStats()
                .build();

        // Create Window TinyLFU cache
        Cache<Integer, String> tinyLfuCache = CacheBuilder.newBuilder()
                .maximumSize(cacheSize)
                .evictionPolicy(EvictionPolicy.WINDOW_TINY_LFU)
                .recordStats()
                .build();

        Random random = new Random(42);

        System.out.println("Simulating realistic mixed workload...");
        System.out.println("  Hot items (20%): accessed 60% of the time");
        System.out.println("  Warm items (30%): accessed 30% of the time");
        System.out.println("  Cold items (50%): accessed 10% of the time");
        System.out.println("  Total accesses: " + numAccesses);

        for (int i = 0; i < numAccesses; i++) {
            int key;
            double rand = random.nextDouble();

            if (rand < 0.60) {
                // Hot items
                key = random.nextInt(numHotItems);
            } else if (rand < 0.90) {
                // Warm items
                key = numHotItems + random.nextInt(numWarmItems);
            } else {
                // Cold items
                key = numHotItems + numWarmItems + random.nextInt(numColdItems);
            }

            String value = "value-" + key;

            // Try to get, if miss then put
            if (lruCache.getIfPresent(key) == null) {
                lruCache.put(key, value);
            }
            if (tinyLfuCache.getIfPresent(key) == null) {
                tinyLfuCache.put(key, value);
            }
        }

        ConcurrentCacheImpl<?, ?> lruImpl = (ConcurrentCacheImpl<?, ?>) lruCache;
        ConcurrentCacheImpl<?, ?> tinyLfuImpl = (ConcurrentCacheImpl<?, ?>) tinyLfuCache;

        double lruHitRate = lruImpl.hitCount() * 100.0 / (lruImpl.hitCount() + lruImpl.missCount());
        double tinyLfuHitRate = tinyLfuImpl.hitCount() * 100.0 / (tinyLfuImpl.hitCount() + tinyLfuImpl.missCount());

        System.out.println("\nResults:");
        System.out.println("  LRU Statistics:");
        System.out.println("    Hit Rate: " + String.format("%.2f", lruHitRate) + "%");
        System.out.println("    Hits: " + lruImpl.hitCount());
        System.out.println("    Misses: " + lruImpl.missCount());
        System.out.println("    Evictions: " + lruImpl.evictionCount());

        System.out.println("\n  TinyLFU Statistics:");
        System.out.println("    Hit Rate: " + String.format("%.2f", tinyLfuHitRate) + "%");
        System.out.println("    Hits: " + tinyLfuImpl.hitCount());
        System.out.println("    Misses: " + tinyLfuImpl.missCount());
        System.out.println("    Evictions: " + tinyLfuImpl.evictionCount());

        System.out.println("\n  Performance Improvement:");
        System.out.println("    Hit Rate Improvement: +" + String.format("%.2f", tinyLfuHitRate - lruHitRate) + " percentage points");
        System.out.println("    Additional Hits: +" + (tinyLfuImpl.hitCount() - lruImpl.hitCount()));
        System.out.println("    Relative Improvement: " + String.format("%.1f", (tinyLfuHitRate / lruHitRate - 1) * 100) + "%");

        if (tinyLfuHitRate > lruHitRate) {
            System.out.println("    ✓ TinyLFU outperforms LRU in realistic mixed workload!");
        }
    }
}
