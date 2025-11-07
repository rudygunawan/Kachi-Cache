package com.github.rudygunawan.kachi.benchmark;

import com.github.rudygunawan.kachi.api.LoadingCache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;

import java.util.*;
import java.util.concurrent.*;

/**
 * Real-world performance benchmark for Kachi Cache with JDK 21.
 * Measures actual throughput and latency.
 */
public class JDK21PerformanceTest {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int TEST_ITERATIONS = 5;

    public static void main(String[] args) throws Exception {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║        Kachi Cache - JDK 21 Performance Benchmark            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("JVM: " + System.getProperty("java.vm.name"));
        System.out.println("Processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println();

        // Test 1: Basic Operations
        testBasicOperations();

        System.out.println("\n" + "═".repeat(80) + "\n");

        // Test 2: Concurrent Throughput
        testConcurrentThroughput();

        System.out.println("\n" + "═".repeat(80) + "\n");

        // Test 3: LoadingCache with I/O Simulation
        testLoadingCacheIO();

        System.out.println("\n" + "═".repeat(80) + "\n");

        // Test 4: Bulk Operations
        testBulkOperations();

        System.out.println("\n" + "═".repeat(80));
        System.out.println("\n✅ Performance benchmark complete!\n");
    }

    private static void testBasicOperations() {
        System.out.println("═══ Test 1: Basic Operations (1 million ops) ═══\n");

        com.github.rudygunawan.kachi.api.Cache<Integer, String> cache =
            CacheBuilder.<Integer, String>newBuilder()
                .maximumSize(10_000)
                .build();

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            for (int i = 0; i < 100_000; i++) {
                cache.put(i, "value" + i);
            }
            for (int i = 0; i < 100_000; i++) {
                cache.getIfPresent(i);
            }
        }

        // Test PUT
        long putStart = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            cache.put(i, "value" + i);
        }
        long putDuration = System.nanoTime() - putStart;
        double putNsPerOp = (double) putDuration / 1_000_000;
        double putOpsPerSec = 1_000_000_000.0 / putNsPerOp;

        System.out.printf("PUT:  %,d ops in %.2f ms\n", 1_000_000, putDuration / 1_000_000.0);
        System.out.printf("      %.0f ns/op, %,.0f ops/sec\n", putNsPerOp, putOpsPerSec);
        System.out.println();

        // Test GET (100% hit rate)
        long getStart = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            cache.getIfPresent(i % 10_000);
        }
        long getDuration = System.nanoTime() - getStart;
        double getNsPerOp = (double) getDuration / 1_000_000;
        double getOpsPerSec = 1_000_000_000.0 / getNsPerOp;

        System.out.printf("GET:  %,d ops in %.2f ms\n", 1_000_000, getDuration / 1_000_000.0);
        System.out.printf("      %.0f ns/op, %,.0f ops/sec\n", getNsPerOp, getOpsPerSec);
    }

    private static void testConcurrentThroughput() throws Exception {
        System.out.println("═══ Test 2: Concurrent Throughput (8 threads) ═══\n");

        int[] threadCounts = {1, 2, 4, 8, 16};
        System.out.println("Threads | Ops/Second    | Throughput Scaling");
        System.out.println("--------|---------------|-------------------");

        long baseline = 0;
        for (int threads : threadCounts) {
            long throughput = measureConcurrentThroughput(threads);

            String scaling = "";
            if (threads == 1) {
                baseline = throughput;
                scaling = "Baseline";
            } else {
                double speedup = (double) throughput / baseline;
                scaling = String.format("%.2fx", speedup);
            }

            System.out.printf("%7d | %,13d | %s\n", threads, throughput, scaling);
        }
    }

    private static long measureConcurrentThroughput(int threads) throws Exception {
        com.github.rudygunawan.kachi.api.Cache<Integer, String> cache =
            CacheBuilder.<Integer, String>newBuilder()
                .maximumSize(100_000)
                .build();

        int opsPerThread = 100_000;

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            runConcurrentBenchmark(cache, threads, opsPerThread / 10);
        }

        // Actual test
        long totalOps = 0;
        long totalTime = 0;

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            runConcurrentBenchmark(cache, threads, opsPerThread);
            long duration = System.nanoTime() - start;

            totalOps += threads * opsPerThread;
            totalTime += duration;
        }

        long avgNanos = totalTime / TEST_ITERATIONS;
        return (totalOps / TEST_ITERATIONS) * 1_000_000_000L / avgNanos;
    }

    private static void runConcurrentBenchmark(
            com.github.rudygunawan.kachi.api.Cache<Integer, String> cache,
            int threads,
            int opsPerThread) throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

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
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
    }

    private static void testLoadingCacheIO() throws Exception {
        System.out.println("═══ Test 3: LoadingCache with I/O (Virtual Threads!) ═══\n");

        int[] loadDelays = {1, 5, 10};
        int[] cacheMisses = {10, 50, 100};

        System.out.println("Scenario                          | Time (ms) | Throughput");
        System.out.println("----------------------------------|-----------|------------");

        for (int delay : loadDelays) {
            for (int misses : cacheMisses) {
                long duration = testLoadingWithDelay(misses, delay);
                double throughput = (double) misses * 1000.0 / duration;

                System.out.printf("%d misses × %dms load          | %9.2f | %,.0f loads/sec\n",
                    misses, delay, duration, throughput);
            }
        }

        System.out.println();
        System.out.println("💡 With virtual threads, all loads run in parallel!");
        System.out.println("   Expected time ≈ load delay (not sum of all loads)");
    }

    private static long testLoadingWithDelay(int misses, int delayMs) throws Exception {
        LoadingCache<Integer, String> cache = CacheBuilder.<Integer, String>newBuilder()
                .maximumSize(1000)
                .build(new com.github.rudygunawan.kachi.api.CacheLoader<Integer, String>() {
                    @Override
                    public String load(Integer key) throws Exception {
                        Thread.sleep(delayMs);  // Simulate I/O
                        return "loaded-" + key;
                    }
                });

        // Warmup
        for (int w = 0; w < 2; w++) {
            for (int i = 0; i < 5; i++) {
                cache.get(i + w * 1000);
            }
        }

        // Actual test
        long totalDuration = 0;
        for (int iter = 0; iter < TEST_ITERATIONS; iter++) {
            List<Integer> keys = new ArrayList<>();
            for (int i = 0; i < misses; i++) {
                keys.add(i + iter * 10000);  // New keys each iteration
            }

            long start = System.nanoTime();
            cache.getAll(keys);
            long duration = System.nanoTime() - start;
            totalDuration += duration;
        }

        return (totalDuration / TEST_ITERATIONS) / 1_000_000;  // Convert to ms
    }

    private static void testBulkOperations() {
        System.out.println("═══ Test 4: Bulk Operations Performance ═══\n");

        com.github.rudygunawan.kachi.api.Cache<Integer, String> cache =
            CacheBuilder.<Integer, String>newBuilder()
                .maximumSize(100_000)
                .build();

        int[] batchSizes = {100, 1000, 10000};

        System.out.println("Operation    | Batch Size | Time (ms) | Ops/Sec");
        System.out.println("-------------|------------|-----------|------------");

        for (int size : batchSizes) {
            // Test putAll
            Map<Integer, String> batch = new HashMap<>();
            for (int i = 0; i < size; i++) {
                batch.put(i, "value" + i);
            }

            // Warmup
            for (int w = 0; w < WARMUP_ITERATIONS; w++) {
                cache.putAll(batch);
            }

            long putAllStart = System.nanoTime();
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                cache.putAll(batch);
            }
            long putAllDuration = (System.nanoTime() - putAllStart) / TEST_ITERATIONS / 1_000_000;
            long putAllOpsPerSec = size * 1000L / putAllDuration;

            System.out.printf("putAll       | %,10d | %9d | %,10d\n",
                size, putAllDuration, putAllOpsPerSec);

            // Test getAllPresent
            List<Integer> keys = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                keys.add(i);
            }

            // Warmup
            for (int w = 0; w < WARMUP_ITERATIONS; w++) {
                cache.getAllPresent(keys);
            }

            long getAllStart = System.nanoTime();
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                cache.getAllPresent(keys);
            }
            long getAllDuration = (System.nanoTime() - getAllStart) / TEST_ITERATIONS / 1_000_000;
            long getAllOpsPerSec = size * 1000L / getAllDuration;

            System.out.printf("getAllPresent| %,10d | %9d | %,10d\n",
                size, getAllDuration, getAllOpsPerSec);
        }
    }
}
