package com.github.rudygunawan.kachi.performance;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Frank Test Suite - JUnit 5 Performance Tests for Kachi Cache Bulk Operations.
 *
 * <p>Named after being "frank" (honest) about performance, Frank Test Suite provides
 * automated JUnit tests that validate bulk operation performance improvements.
 *
 * <p><b>Frank Test gives you the frank truth about your cache performance.</b>
 *
 * <p>Tests validate that bulk operations provide measurable performance improvements
 * over individual operations for various batch sizes and concurrency scenarios.
 *
 * <p>Run with Maven:
 * <pre>
 * mvn test -Dtest=FrankTestSuite
 * </pre>
 *
 * @see <a href="../../../../../FRANK_TEST.md">Frank Test Documentation</a>
 */
public class FrankTestSuite {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int TEST_ITERATIONS = 10;

    /**
     * Test putAll() performance vs individual put() operations.
     */
    @Test
    public void testPutAllPerformance() {
        System.out.println("\n=== FRANK TEST: PUT Operations ===\n");

        int[] batchSizes = {10, 100, 1000, 10000};

        for (int batchSize : batchSizes) {
            System.out.println("Batch size: " + batchSize);
            System.out.println("-".repeat(50));

            // Prepare test data
            Map<String, Integer> testData = new HashMap<>();
            for (int i = 0; i < batchSize; i++) {
                testData.put("key" + i, i);
            }

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                runIndividualPuts(batchSize, testData);
                runBulkPut(batchSize, testData);
            }

            // Test individual puts
            long individualTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                individualTime += runIndividualPuts(batchSize, testData);
            }
            long avgIndividualTime = individualTime / TEST_ITERATIONS;

            // Test bulk put
            long bulkTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                bulkTime += runBulkPut(batchSize, testData);
            }
            long avgBulkTime = bulkTime / TEST_ITERATIONS;

            // Results
            double speedup = (double) avgIndividualTime / avgBulkTime;
            System.out.printf("Individual put() × %d: %,d ns (%.2f ms)%n",
                batchSize, avgIndividualTime, avgIndividualTime / 1_000_000.0);
            System.out.printf("Bulk putAll():        %,d ns (%.2f ms)%n",
                avgBulkTime, avgBulkTime / 1_000_000.0);
            System.out.printf("Speedup:              %.2fx%n", speedup);
            System.out.printf("Time saved:           %,d ns (%.2f ms)%n%n",
                avgIndividualTime - avgBulkTime,
                (avgIndividualTime - avgBulkTime) / 1_000_000.0);

            // Verify speedup improves with batch size
            if (batchSize >= 100) {
                assertTrue(speedup >= 1.0,
                    "Bulk operation should be at least as fast as individual operations");
            }
        }
    }

    /**
     * Test invalidateAll() performance vs individual invalidate() operations.
     */
    @Test
    public void testInvalidateAllPerformance() {
        System.out.println("\n=== FRANK TEST: DELETE Operations ===\n");

        int[] batchSizes = {10, 100, 1000, 10000};

        for (int batchSize : batchSizes) {
            System.out.println("Batch size: " + batchSize);
            System.out.println("-".repeat(50));

            // Prepare keys to delete
            List<String> keysToDelete = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                keysToDelete.add("key" + i);
            }

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                runIndividualInvalidates(batchSize, keysToDelete);
                runBulkInvalidate(batchSize, keysToDelete);
            }

            // Test individual invalidates
            long individualTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                individualTime += runIndividualInvalidates(batchSize, keysToDelete);
            }
            long avgIndividualTime = individualTime / TEST_ITERATIONS;

            // Test bulk invalidate
            long bulkTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                bulkTime += runBulkInvalidate(batchSize, keysToDelete);
            }
            long avgBulkTime = bulkTime / TEST_ITERATIONS;

            // Results
            double speedup = (double) avgIndividualTime / avgBulkTime;
            System.out.printf("Individual invalidate() × %d: %,d ns (%.2f ms)%n",
                batchSize, avgIndividualTime, avgIndividualTime / 1_000_000.0);
            System.out.printf("Bulk invalidateAll():        %,d ns (%.2f ms)%n",
                avgBulkTime, avgBulkTime / 1_000_000.0);
            System.out.printf("Speedup:                     %.2fx%n", speedup);
            System.out.printf("Time saved:                  %,d ns (%.2f ms)%n%n",
                avgIndividualTime - avgBulkTime,
                (avgIndividualTime - avgBulkTime) / 1_000_000.0);

            // Verify speedup improves with batch size
            if (batchSize >= 100) {
                assertTrue(speedup >= 1.0,
                    "Bulk operation should be at least as fast as individual operations");
            }
        }
    }

    /**
     * Test getAllPresent() performance vs individual getIfPresent() operations.
     */
    @Test
    public void testGetAllPresentPerformance() {
        System.out.println("\n=== FRANK TEST: GET Operations ===\n");

        int[] batchSizes = {10, 100, 1000, 10000};

        for (int batchSize : batchSizes) {
            System.out.println("Batch size: " + batchSize);
            System.out.println("-".repeat(50));

            // Prepare keys to retrieve
            List<String> keysToGet = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                keysToGet.add("key" + i);
            }

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                runIndividualGets(batchSize, keysToGet);
                runBulkGet(batchSize, keysToGet);
            }

            // Test individual gets
            long individualTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                individualTime += runIndividualGets(batchSize, keysToGet);
            }
            long avgIndividualTime = individualTime / TEST_ITERATIONS;

            // Test bulk get
            long bulkTime = 0;
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                bulkTime += runBulkGet(batchSize, keysToGet);
            }
            long avgBulkTime = bulkTime / TEST_ITERATIONS;

            // Results
            double speedup = (double) avgIndividualTime / avgBulkTime;
            System.out.printf("Individual getIfPresent() × %d: %,d ns (%.2f ms)%n",
                batchSize, avgIndividualTime, avgIndividualTime / 1_000_000.0);
            System.out.printf("Bulk getAllPresent():           %,d ns (%.2f ms)%n",
                avgBulkTime, avgBulkTime / 1_000_000.0);
            System.out.printf("Speedup:                        %.2fx%n", speedup);
            System.out.printf("Time saved:                     %,d ns (%.2f ms)%n%n",
                avgIndividualTime - avgBulkTime,
                (avgIndividualTime - avgBulkTime) / 1_000_000.0);

            // Verify speedup improves with batch size
            if (batchSize >= 100) {
                assertTrue(speedup >= 1.0,
                    "Bulk operation should be at least as fast as individual operations");
            }
        }
    }

    /**
     * Test bulk operations under concurrent load.
     */
    @Test
    public void testConcurrentBulkOperations() throws InterruptedException, ExecutionException {
        System.out.println("\n=== Concurrent Bulk Operations Test ===\n");

        int numThreads = 10;
        int batchSize = 1000;
        int operationsPerThread = 100;

        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(100000)
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Test concurrent putAll
        System.out.println("Testing concurrent putAll() with " + numThreads + " threads");
        long startTime = System.nanoTime();

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    Map<String, Integer> batch = new HashMap<>();
                    for (int j = 0; j < batchSize; j++) {
                        String key = "thread" + threadId + "_batch" + i + "_key" + j;
                        batch.put(key, j);
                    }
                    cache.putAll(batch);
                }
            }));
        }

        // Wait for completion
        for (Future<?> future : futures) {
            future.get();
        }

        long putAllTime = System.nanoTime() - startTime;
        int expectedEntries = numThreads * operationsPerThread * batchSize;
        long actualEntries = cache.size();

        System.out.printf("Inserted: %,d entries%n", actualEntries);
        System.out.printf("Time: %.2f ms%n", putAllTime / 1_000_000.0);
        System.out.printf("Throughput: %,d ops/sec%n",
            (long) ((expectedEntries / (putAllTime / 1_000_000_000.0))));

        // Test concurrent invalidateAll
        System.out.println("\nTesting concurrent invalidateAll() with " + numThreads + " threads");

        startTime = System.nanoTime();
        futures.clear();

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    List<String> keysToDelete = new ArrayList<>();
                    for (int j = 0; j < batchSize; j++) {
                        String key = "thread" + threadId + "_batch" + i + "_key" + j;
                        keysToDelete.add(key);
                    }
                    cache.invalidateAll(keysToDelete);
                }
            }));
        }

        // Wait for completion
        for (Future<?> future : futures) {
            future.get();
        }

        long invalidateAllTime = System.nanoTime() - startTime;
        long remainingEntries = cache.size();

        System.out.printf("Deleted: %,d entries%n", actualEntries - remainingEntries);
        System.out.printf("Time: %.2f ms%n", invalidateAllTime / 1_000_000.0);
        System.out.printf("Throughput: %,d ops/sec%n",
            (long) ((expectedEntries / (invalidateAllTime / 1_000_000_000.0))));

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    /**
     * Test mixed workload (concurrent reads, writes, deletes).
     */
    @Test
    public void testMixedWorkload() throws InterruptedException, ExecutionException {
        System.out.println("\n=== Mixed Workload Test ===\n");

        int numThreads = 10;
        int batchSize = 100;
        int operationsPerThread = 100;

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

        // Create mixed workload: 50% reads, 30% writes, 20% deletes
        Random random = new Random(42);
        for (int t = 0; t < numThreads; t++) {
            futures.add(executor.submit(() -> {
                Random threadRandom = new Random(random.nextInt());
                for (int i = 0; i < operationsPerThread; i++) {
                    double operation = threadRandom.nextDouble();

                    if (operation < 0.5) {
                        // Read operation (50%)
                        List<String> keysToGet = IntStream.range(0, batchSize)
                            .mapToObj(j -> "key" + threadRandom.nextInt(20000))
                            .collect(Collectors.toList());
                        cache.getAllPresent(keysToGet);
                    } else if (operation < 0.8) {
                        // Write operation (30%)
                        Map<String, Integer> batch = new HashMap<>();
                        for (int j = 0; j < batchSize; j++) {
                            String key = "key" + threadRandom.nextInt(20000);
                            batch.put(key, threadRandom.nextInt());
                        }
                        cache.putAll(batch);
                    } else {
                        // Delete operation (20%)
                        List<String> keysToDelete = IntStream.range(0, batchSize / 2)
                            .mapToObj(j -> "key" + threadRandom.nextInt(20000))
                            .collect(Collectors.toList());
                        cache.invalidateAll(keysToDelete);
                    }
                }
            }));
        }

        // Wait for completion
        for (Future<?> future : futures) {
            future.get();
        }

        long totalTime = System.nanoTime() - startTime;
        int totalOperations = numThreads * operationsPerThread;

        System.out.printf("Threads: %d%n", numThreads);
        System.out.printf("Total operations: %,d%n", totalOperations);
        System.out.printf("Time: %.2f ms%n", totalTime / 1_000_000.0);
        System.out.printf("Throughput: %,d ops/sec%n",
            (long) ((totalOperations / (totalTime / 1_000_000_000.0))));
        System.out.printf("Average latency: %.2f ms%n",
            (totalTime / 1_000_000.0) / totalOperations);
        System.out.printf("Final cache size: %,d%n", cache.size());
        System.out.printf("Hit rate: %.2f%%%n", cache.stats().hitRate() * 100);

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    // Helper methods

    private long runIndividualPuts(int batchSize, Map<String, Integer> testData) {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(batchSize * 2)
                .build();

        long startTime = System.nanoTime();
        for (Map.Entry<String, Integer> entry : testData.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
        }
        long endTime = System.nanoTime();

        assertEquals(batchSize, cache.size(), "All entries should be inserted");
        return endTime - startTime;
    }

    private long runBulkPut(int batchSize, Map<String, Integer> testData) {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(batchSize * 2)
                .build();

        long startTime = System.nanoTime();
        cache.putAll(testData);
        long endTime = System.nanoTime();

        assertEquals(batchSize, cache.size(), "All entries should be inserted");
        return endTime - startTime;
    }

    private long runIndividualInvalidates(int batchSize, List<String> keysToDelete) {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(batchSize * 2)
                .build();

        // Populate cache
        for (int i = 0; i < batchSize; i++) {
            cache.put("key" + i, i);
        }

        long startTime = System.nanoTime();
        for (String key : keysToDelete) {
            cache.invalidate(key);
        }
        long endTime = System.nanoTime();

        assertEquals(0, cache.size(), "All entries should be deleted");
        return endTime - startTime;
    }

    private long runBulkInvalidate(int batchSize, List<String> keysToDelete) {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(batchSize * 2)
                .build();

        // Populate cache
        for (int i = 0; i < batchSize; i++) {
            cache.put("key" + i, i);
        }

        long startTime = System.nanoTime();
        cache.invalidateAll(keysToDelete);
        long endTime = System.nanoTime();

        assertEquals(0, cache.size(), "All entries should be deleted");
        return endTime - startTime;
    }

    private long runIndividualGets(int batchSize, List<String> keysToGet) {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(batchSize * 2)
                .build();

        // Populate cache
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
        long endTime = System.nanoTime();

        assertEquals(batchSize, results.size(), "All entries should be retrieved");
        return endTime - startTime;
    }

    private long runBulkGet(int batchSize, List<String> keysToGet) {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(batchSize * 2)
                .build();

        // Populate cache
        for (int i = 0; i < batchSize; i++) {
            cache.put("key" + i, i);
        }

        long startTime = System.nanoTime();
        Map<String, Integer> results = cache.getAllPresent(keysToGet);
        long endTime = System.nanoTime();

        assertEquals(batchSize, results.size(), "All entries should be retrieved");
        return endTime - startTime;
    }
}
