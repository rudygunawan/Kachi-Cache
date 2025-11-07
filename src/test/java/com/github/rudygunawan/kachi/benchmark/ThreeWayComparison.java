package com.github.rudygunawan.kachi.benchmark;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.rudygunawan.kachi.builder.CacheBuilder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Head-to-head 3-way performance comparison:
 * Kachi vs Caffeine vs Guava
 *
 * Tests identical operations with same parameters.
 */
public class ThreeWayComparison {

    private static final int CACHE_SIZE = 100_000;
    private static final int OPERATIONS = 100_000;
    private static final int WARMUP = 3;
    private static final int ITERATIONS = 5;

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("   3-WAY CACHE PERFORMANCE COMPARISON (JDK 21)");
        System.out.println("   Kachi vs Caffeine vs Guava");
        System.out.println("═══════════════════════════════════════════════════════════\n");

        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("Processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Cache Size: " + CACHE_SIZE);
        System.out.println("Operations: " + OPERATIONS + " per test\n");

        // Test 1: Single-threaded GET performance
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("TEST 1: Single-Threaded GET Performance");
        System.out.println("═══════════════════════════════════════════════════════════\n");
        testSingleThreadedGet();

        // Test 2: Single-threaded PUT performance
        System.out.println("\n═══════════════════════════════════════════════════════════");
        System.out.println("TEST 2: Single-Threaded PUT Performance");
        System.out.println("═══════════════════════════════════════════════════════════\n");
        testSingleThreadedPut();

        // Test 3: Mixed workload (80% GET, 20% PUT)
        System.out.println("\n═══════════════════════════════════════════════════════════");
        System.out.println("TEST 3: Mixed Workload (80% GET, 20% PUT)");
        System.out.println("═══════════════════════════════════════════════════════════\n");
        testMixedWorkload();

        // Test 4: Concurrent throughput
        System.out.println("\n═══════════════════════════════════════════════════════════");
        System.out.println("TEST 4: Concurrent Throughput (16 threads)");
        System.out.println("═══════════════════════════════════════════════════════════\n");
        testConcurrentThroughput(16);

        // Summary
        System.out.println("\n═══════════════════════════════════════════════════════════");
        System.out.println("   COMPARISON COMPLETE!");
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    private static void testSingleThreadedGet() {
        // Kachi
        var kachiCache = CacheBuilder.newBuilder()
            .maximumSize(CACHE_SIZE)
            .build();

        // Caffeine
        Cache<Integer, String> caffeineCache = Caffeine.newBuilder()
            .maximumSize(CACHE_SIZE)
            .build();

        // Guava
        com.google.common.cache.Cache<Integer, String> guavaCache =
            com.google.common.cache.CacheBuilder.newBuilder()
                .maximumSize(CACHE_SIZE)
                .build();

        // Pre-populate all caches
        for (int i = 0; i < CACHE_SIZE; i++) {
            String value = "value-" + i;
            kachiCache.put(i, value);
            caffeineCache.put(i, value);
            guavaCache.put(i, value);
        }

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < OPERATIONS; i++) {
                int key = i % CACHE_SIZE;
                kachiCache.getIfPresent(key);
                caffeineCache.getIfPresent(key);
                guavaCache.getIfPresent(key);
            }
        }

        // Benchmark Kachi
        long kachiTotal = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS; i++) {
                int key = i % CACHE_SIZE;
                kachiCache.getIfPresent(key);
            }
            kachiTotal += System.nanoTime() - start;
        }
        long kachiAvg = kachiTotal / ITERATIONS;
        long kachiPerOp = kachiAvg / OPERATIONS;
        long kachiOpsPerSec = (long) ((double) OPERATIONS / (kachiAvg / 1_000_000_000.0));

        // Benchmark Caffeine
        long caffeineTotal = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS; i++) {
                int key = i % CACHE_SIZE;
                caffeineCache.getIfPresent(key);
            }
            caffeineTotal += System.nanoTime() - start;
        }
        long caffeineAvg = caffeineTotal / ITERATIONS;
        long caffeinePerOp = caffeineAvg / OPERATIONS;
        long caffeineOpsPerSec = (long) ((double) OPERATIONS / (caffeineAvg / 1_000_000_000.0));

        // Benchmark Guava
        long guavaTotal = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS; i++) {
                int key = i % CACHE_SIZE;
                guavaCache.getIfPresent(key);
            }
            guavaTotal += System.nanoTime() - start;
        }
        long guavaAvg = guavaTotal / ITERATIONS;
        long guavaPerOp = guavaAvg / OPERATIONS;
        long guavaOpsPerSec = (long) ((double) OPERATIONS / (guavaAvg / 1_000_000_000.0));

        // Results
        System.out.printf("Kachi:    %,6d ns/op  (%,12d ops/sec)\n", kachiPerOp, kachiOpsPerSec);
        System.out.printf("Caffeine: %,6d ns/op  (%,12d ops/sec)\n", caffeinePerOp, caffeineOpsPerSec);
        System.out.printf("Guava:    %,6d ns/op  (%,12d ops/sec)\n", guavaPerOp, guavaOpsPerSec);

        // Winner
        String winner = "Kachi";
        long bestTime = kachiPerOp;
        if (caffeinePerOp < bestTime) {
            winner = "Caffeine";
            bestTime = caffeinePerOp;
        }
        if (guavaPerOp < bestTime) {
            winner = "Guava";
            bestTime = guavaPerOp;
        }
        System.out.println("\n🏆 Winner: " + winner + " (" + bestTime + " ns/op)");
    }

    private static void testSingleThreadedPut() {
        // Benchmark Kachi
        var kachiCache = CacheBuilder.newBuilder()
            .maximumSize(CACHE_SIZE)
            .build();

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < OPERATIONS; i++) {
                kachiCache.put(i, "value-" + i);
            }
        }

        long kachiTotal = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            kachiCache = CacheBuilder.newBuilder()
                .maximumSize(CACHE_SIZE)
                .build();
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS; i++) {
                kachiCache.put(i, "value-" + i);
            }
            kachiTotal += System.nanoTime() - start;
        }
        long kachiAvg = kachiTotal / ITERATIONS;
        long kachiPerOp = kachiAvg / OPERATIONS;
        long kachiOpsPerSec = (long) ((double) OPERATIONS / (kachiAvg / 1_000_000_000.0));

        // Benchmark Caffeine
        Cache<Integer, String> caffeineCache = Caffeine.newBuilder()
            .maximumSize(CACHE_SIZE)
            .build();

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < OPERATIONS; i++) {
                caffeineCache.put(i, "value-" + i);
            }
        }

        long caffeineTotal = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            caffeineCache = Caffeine.newBuilder()
                .maximumSize(CACHE_SIZE)
                .build();
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS; i++) {
                caffeineCache.put(i, "value-" + i);
            }
            caffeineTotal += System.nanoTime() - start;
        }
        long caffeineAvg = caffeineTotal / ITERATIONS;
        long caffeinePerOp = caffeineAvg / OPERATIONS;
        long caffeineOpsPerSec = (long) ((double) OPERATIONS / (caffeineAvg / 1_000_000_000.0));

        // Benchmark Guava
        com.google.common.cache.Cache<Integer, String> guavaCache =
            GuavaCacheBuilder.newBuilder()
                .maximumSize(CACHE_SIZE)
                .build();

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < OPERATIONS; i++) {
                guavaCache.put(i, "value-" + i);
            }
        }

        long guavaTotal = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            guavaCache = GuavaCacheBuilder.newBuilder()
                .maximumSize(CACHE_SIZE)
                .build();
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS; i++) {
                guavaCache.put(i, "value-" + i);
            }
            guavaTotal += System.nanoTime() - start;
        }
        long guavaAvg = guavaTotal / ITERATIONS;
        long guavaPerOp = guavaAvg / OPERATIONS;
        long guavaOpsPerSec = (long) ((double) OPERATIONS / (guavaAvg / 1_000_000_000.0));

        // Results
        System.out.printf("Kachi:    %,6d ns/op  (%,12d ops/sec)\n", kachiPerOp, kachiOpsPerSec);
        System.out.printf("Caffeine: %,6d ns/op  (%,12d ops/sec)\n", caffeinePerOp, caffeineOpsPerSec);
        System.out.printf("Guava:    %,6d ns/op  (%,12d ops/sec)\n", guavaPerOp, guavaOpsPerSec);

        // Winner
        String winner = "Kachi";
        long bestTime = kachiPerOp;
        if (caffeinePerOp < bestTime) {
            winner = "Caffeine";
            bestTime = caffeinePerOp;
        }
        if (guavaPerOp < bestTime) {
            winner = "Guava";
            bestTime = guavaPerOp;
        }
        System.out.println("\n🏆 Winner: " + winner + " (" + bestTime + " ns/op)");
    }

    private static void testMixedWorkload() {
        // Kachi
        var kachiCache = CacheBuilder.newBuilder()
            .maximumSize(CACHE_SIZE)
            .build();

        // Caffeine
        Cache<Integer, String> caffeineCache = Caffeine.newBuilder()
            .maximumSize(CACHE_SIZE)
            .build();

        // Guava
        com.google.common.cache.Cache<Integer, String> guavaCache =
            com.google.common.cache.CacheBuilder.newBuilder()
                .maximumSize(CACHE_SIZE)
                .build();

        // Warmup
        for (int w = 0; w < WARMUP; w++) {
            for (int i = 0; i < OPERATIONS; i++) {
                int key = i % CACHE_SIZE;
                if (i % 5 == 0) { // 20% writes
                    kachiCache.put(key, "value-" + i);
                    caffeineCache.put(key, "value-" + i);
                    guavaCache.put(key, "value-" + i);
                } else { // 80% reads
                    kachiCache.getIfPresent(key);
                    caffeineCache.getIfPresent(key);
                    guavaCache.getIfPresent(key);
                }
            }
        }

        // Benchmark Kachi
        long kachiTotal = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS; i++) {
                int key = i % CACHE_SIZE;
                if (i % 5 == 0) {
                    kachiCache.put(key, "value-" + i);
                } else {
                    kachiCache.getIfPresent(key);
                }
            }
            kachiTotal += System.nanoTime() - start;
        }
        long kachiAvg = kachiTotal / ITERATIONS;
        long kachiPerOp = kachiAvg / OPERATIONS;
        long kachiOpsPerSec = (long) ((double) OPERATIONS / (kachiAvg / 1_000_000_000.0));

        // Benchmark Caffeine
        long caffeineTotal = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS; i++) {
                int key = i % CACHE_SIZE;
                if (i % 5 == 0) {
                    caffeineCache.put(key, "value-" + i);
                } else {
                    caffeineCache.getIfPresent(key);
                }
            }
            caffeineTotal += System.nanoTime() - start;
        }
        long caffeineAvg = caffeineTotal / ITERATIONS;
        long caffeinePerOp = caffeineAvg / OPERATIONS;
        long caffeineOpsPerSec = (long) ((double) OPERATIONS / (caffeineAvg / 1_000_000_000.0));

        // Benchmark Guava
        long guavaTotal = 0;
        for (int iter = 0; iter < ITERATIONS; iter++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS; i++) {
                int key = i % CACHE_SIZE;
                if (i % 5 == 0) {
                    guavaCache.put(key, "value-" + i);
                } else {
                    guavaCache.getIfPresent(key);
                }
            }
            guavaTotal += System.nanoTime() - start;
        }
        long guavaAvg = guavaTotal / ITERATIONS;
        long guavaPerOp = guavaAvg / OPERATIONS;
        long guavaOpsPerSec = (long) ((double) OPERATIONS / (guavaAvg / 1_000_000_000.0));

        // Results
        System.out.printf("Kachi:    %,6d ns/op  (%,12d ops/sec)\n", kachiPerOp, kachiOpsPerSec);
        System.out.printf("Caffeine: %,6d ns/op  (%,12d ops/sec)\n", caffeinePerOp, caffeineOpsPerSec);
        System.out.printf("Guava:    %,6d ns/op  (%,12d ops/sec)\n", guavaPerOp, guavaOpsPerSec);

        // Winner
        String winner = "Kachi";
        long bestTime = kachiPerOp;
        if (caffeinePerOp < bestTime) {
            winner = "Caffeine";
            bestTime = caffeinePerOp;
        }
        if (guavaPerOp < bestTime) {
            winner = "Guava";
            bestTime = guavaPerOp;
        }
        System.out.println("\n🏆 Winner: " + winner + " (" + bestTime + " ns/op)");
    }

    private static void testConcurrentThroughput(int numThreads) throws Exception {
        final int opsPerThread = OPERATIONS / numThreads;

        // Test Kachi
        var kachiCache = CacheBuilder.newBuilder()
            .maximumSize(CACHE_SIZE)
            .build();

        // Pre-populate
        for (int i = 0; i < CACHE_SIZE; i++) {
            kachiCache.put(i, "value-" + i);
        }

        // Warmup
        ExecutorService warmupService = Executors.newFixedThreadPool(numThreads);
        for (int t = 0; t < numThreads; t++) {
            warmupService.submit(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    int key = i % CACHE_SIZE;
                    kachiCache.getIfPresent(key);
                }
            });
        }
        warmupService.shutdown();
        warmupService.awaitTermination(1, TimeUnit.MINUTES);

        // Benchmark
        ExecutorService kachiService = Executors.newFixedThreadPool(numThreads);
        CountDownLatch kachiLatch = new CountDownLatch(numThreads);
        long kachiStart = System.nanoTime();

        for (int t = 0; t < numThreads; t++) {
            kachiService.submit(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    int key = i % CACHE_SIZE;
                    if (i % 5 == 0) {
                        kachiCache.put(key, "value-" + i);
                    } else {
                        kachiCache.getIfPresent(key);
                    }
                }
                kachiLatch.countDown();
            });
        }

        kachiLatch.await();
        long kachiTime = System.nanoTime() - kachiStart;
        kachiService.shutdown();
        long kachiOpsPerSec = (long) ((double) OPERATIONS / (kachiTime / 1_000_000_000.0));

        // Test Caffeine
        Cache<Integer, String> caffeineCache = Caffeine.newBuilder()
            .maximumSize(CACHE_SIZE)
            .build();

        // Pre-populate
        for (int i = 0; i < CACHE_SIZE; i++) {
            caffeineCache.put(i, "value-" + i);
        }

        // Warmup
        ExecutorService warmupService2 = Executors.newFixedThreadPool(numThreads);
        for (int t = 0; t < numThreads; t++) {
            warmupService2.submit(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    int key = i % CACHE_SIZE;
                    caffeineCache.getIfPresent(key);
                }
            });
        }
        warmupService2.shutdown();
        warmupService2.awaitTermination(1, TimeUnit.MINUTES);

        // Benchmark
        ExecutorService caffeineService = Executors.newFixedThreadPool(numThreads);
        CountDownLatch caffeineLatch = new CountDownLatch(numThreads);
        long caffeineStart = System.nanoTime();

        for (int t = 0; t < numThreads; t++) {
            caffeineService.submit(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    int key = i % CACHE_SIZE;
                    if (i % 5 == 0) {
                        caffeineCache.put(key, "value-" + i);
                    } else {
                        caffeineCache.getIfPresent(key);
                    }
                }
                caffeineLatch.countDown();
            });
        }

        caffeineLatch.await();
        long caffeineTime = System.nanoTime() - caffeineStart;
        caffeineService.shutdown();
        long caffeineOpsPerSec = (long) ((double) OPERATIONS / (caffeineTime / 1_000_000_000.0));

        // Test Guava
        com.google.common.cache.Cache<Integer, String> guavaCache =
            GuavaCacheBuilder.newBuilder()
                .maximumSize(CACHE_SIZE)
                .build();

        // Pre-populate
        for (int i = 0; i < CACHE_SIZE; i++) {
            guavaCache.put(i, "value-" + i);
        }

        // Warmup
        ExecutorService warmupService3 = Executors.newFixedThreadPool(numThreads);
        for (int t = 0; t < numThreads; t++) {
            warmupService3.submit(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    int key = i % CACHE_SIZE;
                    guavaCache.getIfPresent(key);
                }
            });
        }
        warmupService3.shutdown();
        warmupService3.awaitTermination(1, TimeUnit.MINUTES);

        // Benchmark
        ExecutorService guavaService = Executors.newFixedThreadPool(numThreads);
        CountDownLatch guavaLatch = new CountDownLatch(numThreads);
        long guavaStart = System.nanoTime();

        for (int t = 0; t < numThreads; t++) {
            guavaService.submit(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    int key = i % CACHE_SIZE;
                    if (i % 5 == 0) {
                        guavaCache.put(key, "value-" + i);
                    } else {
                        guavaCache.getIfPresent(key);
                    }
                }
                guavaLatch.countDown();
            });
        }

        guavaLatch.await();
        long guavaTime = System.nanoTime() - guavaStart;
        guavaService.shutdown();
        long guavaOpsPerSec = (long) ((double) OPERATIONS / (guavaTime / 1_000_000_000.0));

        // Results
        System.out.printf("Kachi:    %,12d ops/sec\n", kachiOpsPerSec);
        System.out.printf("Caffeine: %,12d ops/sec\n", caffeineOpsPerSec);
        System.out.printf("Guava:    %,12d ops/sec\n", guavaOpsPerSec);

        // Winner
        String winner = "Kachi";
        long bestOps = kachiOpsPerSec;
        if (caffeineOpsPerSec > bestOps) {
            winner = "Caffeine";
            bestOps = caffeineOpsPerSec;
        }
        if (guavaOpsPerSec > bestOps) {
            winner = "Guava";
            bestOps = guavaOpsPerSec;
        }
        System.out.println("\n🏆 Winner: " + winner + " (" + String.format("%,d", bestOps) + " ops/sec)");
    }
}
