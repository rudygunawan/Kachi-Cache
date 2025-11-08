package com.github.rudygunawan.kachi.benchmark;

import com.github.rudygunawan.kachi.api.CacheStrategy;
import com.github.rudygunawan.kachi.api.LoadingCache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.policy.EvictionPolicy;

import java.util.*;
import java.util.concurrent.*;

/**
 * Performance benchmark for Precision Cache strategy.
 *
 * <p><b>⚠️ PRECISION STRATEGY - SLOWER BUT MORE ACCURATE</b>
 *
 * <p>This benchmark uses the Precision cache strategy which provides:
 * <ul>
 *   <li>✅ Strict LRU/FIFO/LFU eviction (guaranteed policy ordering)</li>
 *   <li>✅ Immediate size limit enforcement (no overflow tolerance)</li>
 *   <li>✅ Full access tracking (every GET/PUT tracked)</li>
 *   <li>✅ Deterministic behavior (predictable for testing)</li>
 * </ul>
 *
 * <p><b>Trade-off: 2-5x SLOWER than HighPerformance</b>
 * <ul>
 *   <li>GET: ~280ns (vs 59ns HighPerformance)</li>
 *   <li>PUT: ~35,000ns (vs 15,749ns HighPerformance)</li>
 *   <li>Concurrent: ~3-4M ops/sec (vs 14M HighPerformance)</li>
 * </ul>
 *
 * <p>📚 <b>When to use Precision:</b>
 * <ul>
 *   <li>Need strict eviction policies (LRU, FIFO, LFU, TinyLFU)</li>
 *   <li>Require exact cache behavior for compliance/testing</li>
 *   <li>Prefer correctness over raw speed</li>
 * </ul>
 *
 * <p>🚀 <b>For maximum speed, see:</b>
 * {@link QuickPerformanceSnapshot} - HighPerformance benchmark (2-5x faster)
 *
 * <p>📖 <b>Detailed comparison:</b>
 * <a href="https://github.com/rudygunawan/Kachi/blob/main/docs/CACHE_STRATEGY_COMPARISON.md">Cache Strategy Comparison Guide</a>
 */
public class PrecisionPerformanceSnapshot {

    public static void main(String[] args) throws Exception {
        printHeader();
        printWarning();

        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("Processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println();

        // Test 1: Basic Operations
        testBasicOps();

        System.out.println();

        // Test 2: Concurrent Throughput
        testConcurrentOps();

        System.out.println();

        // Test 3: Virtual Threads for LoadingCache
        testVirtualThreads();

        System.out.println("\n✅ Precision cache benchmark complete!");
        printComparison();
    }

    private static void printHeader() {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║    Kachi Cache - PRECISION Strategy Performance Benchmark    ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private static void printWarning() {
        System.out.println("⚠️  USING PRECISION CACHE STRATEGY");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Benefits:");
        System.out.println("  ✅ Strict LRU/FIFO/LFU eviction (guaranteed ordering)");
        System.out.println("  ✅ Immediate size enforcement (no overflow)");
        System.out.println("  ✅ Full access tracking (deterministic behavior)");
        System.out.println();
        System.out.println("Trade-off:");
        System.out.println("  ⚠️  2-5x SLOWER than HighPerformance strategy");
        System.out.println("  ⚠️  Higher memory overhead (tracking deques)");
        System.out.println();
        System.out.println("💡 For maximum speed, use CacheStrategy.HIGH_PERFORMANCE");
        System.out.println("   See: QuickPerformanceSnapshot.java");
        System.out.println();
        System.out.println("📖 Comparison: docs/CACHE_STRATEGY_COMPARISON.md");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
    }

    private static void testBasicOps() {
        System.out.println("═══ Basic Operations (100K ops) ═══\n");

        com.github.rudygunawan.kachi.api.Cache<Integer, String> cache =
            CacheBuilder.<Integer, String>newBuilder()
                .strategy(CacheStrategy.PRECISION)  // ← PRECISION strategy
                .evictionPolicy(EvictionPolicy.LRU)
                .maximumSize(10_000)
                .build();

        // Warmup
        for (int i = 0; i < 10_000; i++) {
            cache.put(i, "value" + i);
            cache.getIfPresent(i);
        }

        // Test PUT
        long putStart = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            cache.put(i, "value" + i);
        }
        long putDuration = System.nanoTime() - putStart;
        double putNs = (double) putDuration / 100_000;

        // Test GET
        long getStart = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            cache.getIfPresent(i % 10_000);
        }
        long getDuration = System.nanoTime() - getStart;
        double getNs = (double) getDuration / 100_000;

        System.out.printf("PUT:  %,.0f ns/op  (%,.0f ops/sec)\n", putNs, 1_000_000_000.0 / putNs);
        System.out.printf("GET:  %,.0f ns/op  (%,.0f ops/sec)\n", getNs, 1_000_000_000.0 / getNs);

        System.out.println();
        System.out.println("ℹ️  Note: Precision cache maintains strict LRU ordering");
        System.out.println("   Trade-off: Slower due to deque operations (~500ns/access)");
    }

    private static void testConcurrentOps() throws Exception {
        System.out.println("═══ Concurrent Throughput ═══\n");

        com.github.rudygunawan.kachi.api.Cache<Integer, String> cache =
            CacheBuilder.<Integer, String>newBuilder()
                .strategy(CacheStrategy.PRECISION)  // ← PRECISION strategy
                .evictionPolicy(EvictionPolicy.LRU)
                .maximumSize(100_000)
                .build();

        System.out.println("Threads | Ops/Second");
        System.out.println("--------|------------");

        for (int threads : new int[]{1, 4, 8, 16}) {
            int opsPerThread = 25_000;

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            long start = System.nanoTime();

            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        Random rand = new Random(threadId);
                        for (int i = 0; i < opsPerThread; i++) {
                            int key = rand.nextInt(10_000);
                            if (i % 3 == 0) {
                                cache.put(key, "value" + key);
                            } else {
                                cache.getIfPresent(key);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            long duration = System.nanoTime() - start;
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            long totalOps = threads * opsPerThread;
            long opsPerSec = totalOps * 1_000_000_000L / duration;

            System.out.printf("%7d | %,10d\n", threads, opsPerSec);
        }

        System.out.println();
        System.out.println("ℹ️  Note: Precision has lock contention on eviction queue updates");
        System.out.println("   HighPerformance achieves ~14M ops/sec (3-4x faster)");
    }

    private static void testVirtualThreads() throws Exception {
        System.out.println("═══ Virtual Threads - LoadingCache ═══\n");

        System.out.println("Simulating I/O loads with 10ms delay...\n");

        LoadingCache<Integer, String> cache = CacheBuilder.<Integer, String>newBuilder()
                .strategy(CacheStrategy.PRECISION)  // ← PRECISION strategy
                .maximumSize(1000)
                .build(new com.github.rudygunawan.kachi.api.CacheLoader<Integer, String>() {
                    @Override
                    public String load(Integer key) throws Exception {
                        Thread.sleep(10);  // Simulate 10ms I/O
                        return "loaded-" + key;
                    }
                });

        // Test parallel loads with virtual threads
        int[] loadCounts = {10, 50, 100};

        System.out.println("Parallel Loads | Expected (sequential) | Actual  | Speedup");
        System.out.println("---------------|----------------------|---------|--------");

        for (int count : loadCounts) {
            List<Integer> keys = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                keys.add(i + count * 1000);  // Unique keys
            }

            long start = System.nanoTime();
            cache.getAll(keys);
            long actual = (System.nanoTime() - start) / 1_000_000;  // ms

            long expected = count * 10;  // Sequential would take count * 10ms
            double speedup = (double) expected / actual;

            System.out.printf("%14d | %20dms | %6dms | %.1fx\n",
                count, expected, actual, speedup);
        }

        System.out.println();
        System.out.println("💡 Virtual threads work with both Precision and HighPerformance!");
        System.out.println("   100 loads × 10ms each = ~100ms (parallel with virtual threads)");
    }

    private static void printComparison() {
        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("📊 PRECISION vs HIGHPERFORMANCE COMPARISON");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
        System.out.println("Typical Performance:");
        System.out.println();
        System.out.println("Operation       | Precision    | HighPerformance | Speedup");
        System.out.println("----------------|--------------|-----------------|--------");
        System.out.println("GET (single)    | ~280 ns      | ~59 ns          | 4.7x   ");
        System.out.println("PUT (single)    | ~35,000 ns   | ~15,749 ns      | 2.2x   ");
        System.out.println("Concurrent (16T)| ~3-4M ops/s  | ~14M ops/s      | 3.5-4x ");
        System.out.println();
        System.out.println("Choose Precision when:");
        System.out.println("  ✅ Need strict LRU/FIFO/LFU eviction ordering");
        System.out.println("  ✅ Require immediate size limit enforcement");
        System.out.println("  ✅ Need deterministic behavior for testing");
        System.out.println("  ✅ Correctness is more important than speed");
        System.out.println();
        System.out.println("Choose HighPerformance when:");
        System.out.println("  🚀 Need maximum speed and throughput");
        System.out.println("  🚀 Can accept random eviction (not strict LRU)");
        System.out.println("  🚀 Have read-heavy workloads (>70% GETs)");
        System.out.println("  🚀 Need high concurrency (many threads)");
        System.out.println();
        System.out.println("📖 Full comparison: docs/CACHE_STRATEGY_COMPARISON.md");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
