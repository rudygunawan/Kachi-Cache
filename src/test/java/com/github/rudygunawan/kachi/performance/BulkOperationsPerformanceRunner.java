package com.github.rudygunawan.kachi.performance;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Standalone performance runner for bulk operations.
 *
 * <p>Run this class directly to generate a comprehensive performance report:
 * <pre>
 * java -cp target/test-classes:target/classes \
 *   com.github.rudygunawan.kachi.performance.BulkOperationsPerformanceRunner
 * </pre>
 */
public class BulkOperationsPerformanceRunner {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int TEST_ITERATIONS = 10;
    private static final NumberFormat numberFormat = NumberFormat.getInstance();
    private static final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");

    public static void main(String[] args) throws Exception {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║   Kachi Cache - Bulk Operations Performance Benchmark        ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("  Test iterations:   " + TEST_ITERATIONS);
        System.out.println("  JVM:              " + System.getProperty("java.version"));
        System.out.println("  OS:               " + System.getProperty("os.name"));
        System.out.println("  Processors:       " + Runtime.getRuntime().availableProcessors());
        System.out.println();

        // Run all benchmarks
        benchmarkPutOperations();
        benchmarkDeleteOperations();
        benchmarkReadOperations();
        benchmarkConcurrentOperations();
        benchmarkMixedWorkload();

        System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    Benchmark Complete                         ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");

        System.exit(0);
    }

    private static void benchmarkPutOperations() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  TEST 1: PUT OPERATIONS (Batch Insert Performance)");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();

        int[] batchSizes = {10, 100, 1000, 10000};

        System.out.printf("%-12s | %-18s | %-18s | %-10s | %-12s%n",
            "Batch Size", "Individual (ms)", "Bulk (ms)", "Speedup", "Time Saved");
        System.out.println("-".repeat(85));

        for (int batchSize : batchSizes) {
            Map<String, Integer> testData = new HashMap<>();
            for (int i = 0; i < batchSize; i++) {
                testData.put("key" + i, i);
            }

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                runIndividualPuts(batchSize, testData);
                runBulkPut(batchSize, testData);
            }

            // Benchmark individual puts
            long individualTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                individualTime += runIndividualPuts(batchSize, testData);
            }
            double avgIndividualMs = (individualTime / TEST_ITERATIONS) / 1_000_000.0;

            // Benchmark bulk put
            long bulkTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                bulkTime += runBulkPut(batchSize, testData);
            }
            double avgBulkMs = (bulkTime / TEST_ITERATIONS) / 1_000_000.0;

            double speedup = avgIndividualMs / avgBulkMs;
            double timeSavedMs = avgIndividualMs - avgBulkMs;

            System.out.printf("%-12s | %18s | %18s | %9sx | %11s ms%n",
                numberFormat.format(batchSize),
                decimalFormat.format(avgIndividualMs),
                decimalFormat.format(avgBulkMs),
                decimalFormat.format(speedup),
                decimalFormat.format(timeSavedMs));
        }

        System.out.println();
    }

    private static void benchmarkDeleteOperations() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  TEST 2: DELETE OPERATIONS (Batch Remove Performance)");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();

        int[] batchSizes = {10, 100, 1000, 10000};

        System.out.printf("%-12s | %-18s | %-18s | %-10s | %-12s%n",
            "Batch Size", "Individual (ms)", "Bulk (ms)", "Speedup", "Time Saved");
        System.out.println("-".repeat(85));

        for (int batchSize : batchSizes) {
            List<String> keysToDelete = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                keysToDelete.add("key" + i);
            }

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                runIndividualInvalidates(batchSize, keysToDelete);
                runBulkInvalidate(batchSize, keysToDelete);
            }

            // Benchmark individual invalidates
            long individualTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                individualTime += runIndividualInvalidates(batchSize, keysToDelete);
            }
            double avgIndividualMs = (individualTime / TEST_ITERATIONS) / 1_000_000.0;

            // Benchmark bulk invalidate
            long bulkTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                bulkTime += runBulkInvalidate(batchSize, keysToDelete);
            }
            double avgBulkMs = (bulkTime / TEST_ITERATIONS) / 1_000_000.0;

            double speedup = avgIndividualMs / avgBulkMs;
            double timeSavedMs = avgIndividualMs - avgBulkMs;

            System.out.printf("%-12s | %18s | %18s | %9sx | %11s ms%n",
                numberFormat.format(batchSize),
                decimalFormat.format(avgIndividualMs),
                decimalFormat.format(avgBulkMs),
                decimalFormat.format(speedup),
                decimalFormat.format(timeSavedMs));
        }

        System.out.println();
    }

    private static void benchmarkReadOperations() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  TEST 3: READ OPERATIONS (Batch Retrieval Performance)");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();

        int[] batchSizes = {10, 100, 1000, 10000};

        System.out.printf("%-12s | %-18s | %-18s | %-10s | %-12s%n",
            "Batch Size", "Individual (ms)", "Bulk (ms)", "Speedup", "Time Saved");
        System.out.println("-".repeat(85));

        for (int batchSize : batchSizes) {
            List<String> keysToGet = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                keysToGet.add("key" + i);
            }

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                runIndividualGets(batchSize, keysToGet);
                runBulkGet(batchSize, keysToGet);
            }

            // Benchmark individual gets
            long individualTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                individualTime += runIndividualGets(batchSize, keysToGet);
            }
            double avgIndividualMs = (individualTime / TEST_ITERATIONS) / 1_000_000.0;

            // Benchmark bulk get
            long bulkTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                bulkTime += runBulkGet(batchSize, keysToGet);
            }
            double avgBulkMs = (bulkTime / TEST_ITERATIONS) / 1_000_000.0;

            double speedup = avgIndividualMs / avgBulkMs;
            double timeSavedMs = avgIndividualMs - avgBulkMs;

            System.out.printf("%-12s | %18s | %18s | %9sx | %11s ms%n",
                numberFormat.format(batchSize),
                decimalFormat.format(avgIndividualMs),
                decimalFormat.format(avgBulkMs),
                decimalFormat.format(speedup),
                decimalFormat.format(timeSavedMs));
        }

        System.out.println();
    }

    private static void benchmarkConcurrentOperations() throws Exception {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  TEST 4: CONCURRENT OPERATIONS (Multi-threaded Performance)");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();

        int[] threadCounts = {1, 2, 4, 8, 16};
        int batchSize = 1000;
        int operationsPerThread = 100;

        System.out.printf("%-10s | %-15s | %-15s | %-15s%n",
            "Threads", "Total Time (ms)", "Throughput", "Entries/sec");
        System.out.println("-".repeat(65));

        for (int numThreads : threadCounts) {
            Cache<String, Integer> cache = CacheBuilder.newBuilder()
                    .maximumSize(numThreads * operationsPerThread * batchSize)
                    .build();

            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            long startTime = System.nanoTime();

            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < numThreads; t++) {
                final int threadId = t;
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < operationsPerThread; i++) {
                        Map<String, Integer> batch = new HashMap<>();
                        for (int j = 0; j < batchSize; j++) {
                            String key = "t" + threadId + "_b" + i + "_k" + j;
                            batch.put(key, j);
                        }
                        cache.putAll(batch);
                    }
                }));
            }

            for (Future<?> future : futures) {
                future.get();
            }

            long totalTime = System.nanoTime() - startTime;
            double totalTimeMs = totalTime / 1_000_000.0;
            int totalEntries = numThreads * operationsPerThread * batchSize;
            long throughput = (long) (totalEntries / (totalTime / 1_000_000_000.0));

            System.out.printf("%-10d | %15s | %15s | %15s%n",
                numThreads,
                decimalFormat.format(totalTimeMs),
                numberFormat.format(totalEntries),
                numberFormat.format(throughput));

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        System.out.println();
    }

    private static void benchmarkMixedWorkload() throws Exception {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  TEST 5: MIXED WORKLOAD (50% Read, 30% Write, 20% Delete)");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println();

        int numThreads = 10;
        int batchSize = 100;
        int operationsPerThread = 200;

        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(50000)
                .recordStats()
                .build();

        // Pre-populate cache
        Map<String, Integer> initialData = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            initialData.put("key" + i, i);
        }
        cache.putAll(initialData);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        long startTime = System.nanoTime();

        List<Future<?>> futures = new ArrayList<>();
        Random random = new Random(42);

        for (int t = 0; t < numThreads; t++) {
            futures.add(executor.submit(() -> {
                Random threadRandom = new Random(random.nextInt());
                for (int i = 0; i < operationsPerThread; i++) {
                    double operation = threadRandom.nextDouble();

                    if (operation < 0.5) {
                        // Read (50%)
                        List<String> keysToGet = IntStream.range(0, batchSize)
                            .mapToObj(j -> "key" + threadRandom.nextInt(20000))
                            .collect(Collectors.toList());
                        cache.getAllPresent(keysToGet);
                    } else if (operation < 0.8) {
                        // Write (30%)
                        Map<String, Integer> batch = new HashMap<>();
                        for (int j = 0; j < batchSize; j++) {
                            String key = "key" + threadRandom.nextInt(20000);
                            batch.put(key, threadRandom.nextInt());
                        }
                        cache.putAll(batch);
                    } else {
                        // Delete (20%)
                        List<String> keysToDelete = IntStream.range(0, batchSize / 2)
                            .mapToObj(j -> "key" + threadRandom.nextInt(20000))
                            .collect(Collectors.toList());
                        cache.invalidateAll(keysToDelete);
                    }
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        long totalTime = System.nanoTime() - startTime;
        int totalOperations = numThreads * operationsPerThread;

        System.out.printf("Workload:         50%% Read, 30%% Write, 20%% Delete%n");
        System.out.printf("Threads:          %d%n", numThreads);
        System.out.printf("Batch size:       %s%n", numberFormat.format(batchSize));
        System.out.printf("Total operations: %s%n", numberFormat.format(totalOperations));
        System.out.printf("Total time:       %s ms%n", decimalFormat.format(totalTime / 1_000_000.0));
        System.out.printf("Throughput:       %s ops/sec%n",
            numberFormat.format((long) (totalOperations / (totalTime / 1_000_000_000.0))));
        System.out.printf("Avg latency:      %s ms%n",
            decimalFormat.format((totalTime / 1_000_000.0) / totalOperations));
        System.out.printf("Final cache size: %s%n", numberFormat.format(cache.size()));
        System.out.printf("Hit rate:         %s%%%n", decimalFormat.format(cache.stats().hitRate() * 100));
        System.out.printf("Evictions:        %s%n", numberFormat.format(cache.stats().evictionCount()));

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println();
    }

    // Helper methods

    private static long runIndividualPuts(int batchSize, Map<String, Integer> testData) {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(batchSize * 2)
                .build();

        long startTime = System.nanoTime();
        for (Map.Entry<String, Integer> entry : testData.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
        }
        return System.nanoTime() - startTime;
    }

    private static long runBulkPut(int batchSize, Map<String, Integer> testData) {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(batchSize * 2)
                .build();

        long startTime = System.nanoTime();
        cache.putAll(testData);
        return System.nanoTime() - startTime;
    }

    private static long runIndividualInvalidates(int batchSize, List<String> keysToDelete) {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(batchSize * 2)
                .build();

        for (int i = 0; i < batchSize; i++) {
            cache.put("key" + i, i);
        }

        long startTime = System.nanoTime();
        for (String key : keysToDelete) {
            cache.invalidate(key);
        }
        return System.nanoTime() - startTime;
    }

    private static long runBulkInvalidate(int batchSize, List<String> keysToDelete) {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(batchSize * 2)
                .build();

        for (int i = 0; i < batchSize; i++) {
            cache.put("key" + i, i);
        }

        long startTime = System.nanoTime();
        cache.invalidateAll(keysToDelete);
        return System.nanoTime() - startTime;
    }

    private static long runIndividualGets(int batchSize, List<String> keysToGet) {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(batchSize * 2)
                .build();

        for (int i = 0; i < batchSize; i++) {
            cache.put("key" + i, i);
        }

        long startTime = System.nanoTime();
        Map<String, Integer> results = new HashMap<>();
        for (String key : keysToGet) {
            Integer value = cache.getIfPresent(key);
            if (value != null) {
                results.put(key, value);
            }
        }
        return System.nanoTime() - startTime;
    }

    private static long runBulkGet(int batchSize, List<String> keysToGet) {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(batchSize * 2)
                .build();

        for (int i = 0; i < batchSize; i++) {
            cache.put("key" + i, i);
        }

        long startTime = System.nanoTime();
        cache.getAllPresent(keysToGet);
        return System.nanoTime() - startTime;
    }
}
