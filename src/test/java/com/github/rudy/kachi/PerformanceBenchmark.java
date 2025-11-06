package com.github.rudy.kachi;

import org.junit.jupiter.api.Test;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance benchmark tests for the cache implementation.
 * These tests measure throughput and latency under various workloads.
 */
class PerformanceBenchmark {

    @Test
    void benchmarkSingleThreadedReads() {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .recordStats()
                .build();

        // Pre-populate cache
        for (int i = 0; i < 10000; i++) {
            cache.put(i, "value_" + i);
        }

        int iterations = 1_000_000;
        Random random = new Random(42);
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            int key = random.nextInt(10000);
            cache.getIfPresent(key);
        }

        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = iterations / (durationMs / 1000.0);

        System.out.println("=== Single-Threaded Read Benchmark ===");
        System.out.println("Operations: " + iterations);
        System.out.println("Duration: " + String.format("%.2f", durationMs) + " ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ops/sec");
        System.out.println("Avg latency: " + String.format("%.2f", (durationMs * 1000) / iterations) + " ns");
        System.out.println(cache.stats());
    }

    @Test
    void benchmarkSingleThreadedWrites() {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .recordStats()
                .build();

        int iterations = 1_000_000;
        Random random = new Random(42);
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            int key = random.nextInt(10000);
            cache.put(key, "value_" + key);
        }

        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = iterations / (durationMs / 1000.0);

        System.out.println("=== Single-Threaded Write Benchmark ===");
        System.out.println("Operations: " + iterations);
        System.out.println("Duration: " + String.format("%.2f", durationMs) + " ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ops/sec");
        System.out.println("Avg latency: " + String.format("%.2f", (durationMs * 1000) / iterations) + " ns");
    }

    @Test
    void benchmarkMixedWorkload() {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .recordStats()
                .build();

        // Pre-populate 50%
        for (int i = 0; i < 5000; i++) {
            cache.put(i, "value_" + i);
        }

        int iterations = 1_000_000;
        Random random = new Random(42);
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            int key = random.nextInt(10000);
            if (random.nextDouble() < 0.8) {
                // 80% reads
                cache.getIfPresent(key);
            } else {
                // 20% writes
                cache.put(key, "value_" + key);
            }
        }

        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = iterations / (durationMs / 1000.0);

        System.out.println("=== Mixed Workload Benchmark (80% reads, 20% writes) ===");
        System.out.println("Operations: " + iterations);
        System.out.println("Duration: " + String.format("%.2f", durationMs) + " ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ops/sec");
        System.out.println("Avg latency: " + String.format("%.2f", (durationMs * 1000) / iterations) + " ns");
        System.out.println(cache.stats());
    }

    @Test
    void benchmarkConcurrentReads() throws InterruptedException {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .recordStats()
                .build();

        // Pre-populate cache
        for (int i = 0; i < 10000; i++) {
            cache.put(i, "value_" + i);
        }

        int numThreads = Runtime.getRuntime().availableProcessors();
        int operationsPerThread = 100_000;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        AtomicLong totalOperations = new AtomicLong(0);

        long startTime = System.nanoTime();
        startLatch.countDown(); // Start all threads

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Random random = new Random();
                    for (int i = 0; i < operationsPerThread; i++) {
                        int key = random.nextInt(10000);
                        cache.getIfPresent(key);
                        totalOperations.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        endLatch.await();
        long endTime = System.nanoTime();
        executor.shutdown();

        double durationMs = (endTime - startTime) / 1_000_000.0;
        long totalOps = totalOperations.get();
        double throughput = totalOps / (durationMs / 1000.0);

        System.out.println("=== Concurrent Read Benchmark ===");
        System.out.println("Threads: " + numThreads);
        System.out.println("Operations: " + totalOps);
        System.out.println("Duration: " + String.format("%.2f", durationMs) + " ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ops/sec");
        System.out.println("Avg latency: " + String.format("%.2f", (durationMs * 1000) / totalOps) + " ns");
        System.out.println(cache.stats());
    }

    @Test
    void benchmarkConcurrentMixedWorkload() throws InterruptedException {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .recordStats()
                .build();

        // Pre-populate 50%
        for (int i = 0; i < 5000; i++) {
            cache.put(i, "value_" + i);
        }

        int numThreads = Runtime.getRuntime().availableProcessors();
        int operationsPerThread = 100_000;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        AtomicLong totalOperations = new AtomicLong(0);

        long startTime = System.nanoTime();
        startLatch.countDown();

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Random random = new Random();
                    for (int i = 0; i < operationsPerThread; i++) {
                        int key = random.nextInt(10000);
                        if (random.nextDouble() < 0.8) {
                            cache.getIfPresent(key);
                        } else {
                            cache.put(key, "value_" + key);
                        }
                        totalOperations.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        endLatch.await();
        long endTime = System.nanoTime();
        executor.shutdown();

        double durationMs = (endTime - startTime) / 1_000_000.0;
        long totalOps = totalOperations.get();
        double throughput = totalOps / (durationMs / 1000.0);

        System.out.println("=== Concurrent Mixed Workload Benchmark (80% reads, 20% writes) ===");
        System.out.println("Threads: " + numThreads);
        System.out.println("Operations: " + totalOps);
        System.out.println("Duration: " + String.format("%.2f", durationMs) + " ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ops/sec");
        System.out.println("Avg latency: " + String.format("%.2f", (durationMs * 1000) / totalOps) + " ns");
        System.out.println(cache.stats());
    }

    @Test
    void benchmarkLoadingCache() throws Exception {
        CacheLoader<Integer, String> loader = new CacheLoader<Integer, String>() {
            @Override
            public String load(Integer key) throws Exception {
                // Simulate database lookup with 1ms delay
                Thread.sleep(1);
                return "loaded_value_" + key;
            }
        };

        LoadingCache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .recordStats()
                .build(loader);

        int iterations = 10000;
        Random random = new Random(42);
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            // Access with high locality (80% hit rate)
            int key = random.nextInt(100);
            cache.get(key);
        }

        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = iterations / (durationMs / 1000.0);

        CacheStats stats = cache.stats();

        System.out.println("=== Loading Cache Benchmark ===");
        System.out.println("Operations: " + iterations);
        System.out.println("Duration: " + String.format("%.2f", durationMs) + " ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ops/sec");
        System.out.println("Hit Rate: " + String.format("%.2f%%", stats.hitRate() * 100));
        System.out.println("Loads: " + stats.loadCount());
        System.out.println("Avg Load Time: " + String.format("%.2f", stats.averageLoadPenalty() / 1_000_000.0) + " ms");
        System.out.println(stats);
    }

    @Test
    void benchmarkEvictionPerformance() {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .recordStats()
                .build();

        int iterations = 100_000;
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            cache.put(i, "value_" + i);
        }

        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = iterations / (durationMs / 1000.0);

        System.out.println("=== Eviction Performance Benchmark ===");
        System.out.println("Operations: " + iterations);
        System.out.println("Max Size: 1000");
        System.out.println("Duration: " + String.format("%.2f", durationMs) + " ms");
        System.out.println("Throughput: " + String.format("%.2f", throughput) + " ops/sec");
        System.out.println("Cache Size: " + cache.size());
        System.out.println("Evictions: " + cache.stats().evictionCount());
    }
}
