package com.github.rudygunawan.kachi.benchmark;

import com.github.rudygunawan.kachi.api.LoadingCache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;

import java.util.*;
import java.util.concurrent.*;

/**
 * Quick performance snapshot showing Kachi Cache with JDK 21 virtual threads.
 */
public class QuickPerformanceSnapshot {

    public static void main(String[] args) throws Exception {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║         Kachi Cache - JDK 21 Performance Snapshot            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
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

        System.out.println("\n✅ Performance snapshot complete!\n");
    }

    private static void testBasicOps() {
        System.out.println("═══ Basic Operations (100K ops) ═══\n");

        com.github.rudygunawan.kachi.api.Cache<Integer, String> cache =
            CacheBuilder.<Integer, String>newBuilder()
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

        System.out.printf("PUT:  %.0f ns/op  (%,.0f ops/sec)\n", putNs, 1_000_000_000.0 / putNs);
        System.out.printf("GET:  %.0f ns/op  (%,.0f ops/sec)\n", getNs, 1_000_000_000.0 / getNs);
    }

    private static void testConcurrentOps() throws Exception {
        System.out.println("═══ Concurrent Throughput ═══\n");

        com.github.rudygunawan.kachi.api.Cache<Integer, String> cache =
            CacheBuilder.<Integer, String>newBuilder()
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
    }

    private static void testVirtualThreads() throws Exception {
        System.out.println("═══ Virtual Threads - LoadingCache ═══\n");

        System.out.println("Simulating I/O loads with 10ms delay...\n");

        LoadingCache<Integer, String> cache = CacheBuilder.<Integer, String>newBuilder()
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
        System.out.println("💡 With virtual threads, loads run in parallel!");
        System.out.println("   100 loads × 10ms each = ~100ms (not 1000ms sequential)");
    }
}
