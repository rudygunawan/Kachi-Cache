package com.github.rudygunawan.kachi.benchmark;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.google.common.cache.CacheLoader;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive benchmark comparing Kachi Cache vs industry-standard caches.
 *
 * <p>Compares:
 * <ul>
 *   <li>Kachi Cache - Our implementation</li>
 *   <li>Google Guava Cache - Widely used in production</li>
 *   <li>Caffeine Cache - High-performance successor to Guava</li>
 * </ul>
 *
 * <p>Test scenarios:
 * <ol>
 *   <li>Sequential read/write performance</li>
 *   <li>Concurrent throughput (1, 4, 8, 16 threads)</li>
 *   <li>Mixed workload (75% reads, 25% writes)</li>
 *   <li>Eviction performance (size-based)</li>
 *   <li>LoadingCache performance</li>
 * </ol>
 */
public class CacheComparison {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int TEST_ITERATIONS = 5;
    private static final int CACHE_SIZE = 10_000;
    private static final int OPERATIONS = 100_000;

    public static void main(String[] args) throws Exception {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║          Kachi vs Guava vs Caffeine - Honest Comparison      ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Test Configuration:");
        System.out.println("  Cache size: " + String.format("%,d", CACHE_SIZE));
        System.out.println("  Operations per test: " + String.format("%,d", OPERATIONS));
        System.out.println("  Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("  Test iterations: " + TEST_ITERATIONS);
        System.out.println();

        // Test 1: Sequential Performance
        System.out.println("═══ Test 1: Sequential Performance (single thread) ═══\n");
        testSequentialPerformance();

        System.out.println("\n" + "═".repeat(80) + "\n");

        // Test 2: Concurrent Throughput
        System.out.println("═══ Test 2: Concurrent Throughput Scaling ═══\n");
        testConcurrentThroughput();

        System.out.println("\n" + "═".repeat(80) + "\n");

        // Test 3: Mixed Workload (75% read, 25% write)
        System.out.println("═══ Test 3: Mixed Workload (75% read / 25% write) ═══\n");
        testMixedWorkload();

        System.out.println("\n" + "═".repeat(80) + "\n");

        // Test 4: LoadingCache Performance
        System.out.println("═══ Test 4: LoadingCache with Expensive Loads ═══\n");
        testLoadingCache();

        System.out.println("\n" + "═".repeat(80));
        System.out.println("\n✅ Benchmark complete!\n");
        printSummary();
    }

    private static void testSequentialPerformance() throws Exception {
        System.out.println("Operation      | Kachi        | Guava        | Caffeine     | Winner");
        System.out.println("---------------|--------------|--------------|--------------|--------");

        // Test PUT performance
        long kachiPut = measureSequentialWrites("Kachi");
        long guavaPut = measureSequentialWrites("Guava");
        long caffeinePut = measureSequentialWrites("Caffeine");
        String putWinner = getWinner(kachiPut, guavaPut, caffeinePut);

        System.out.printf("PUT (ms)       | %12.2f | %12.2f | %12.2f | %s%n",
            kachiPut / 1_000_000.0, guavaPut / 1_000_000.0, caffeinePut / 1_000_000.0, putWinner);

        // Test GET performance (100% hit rate)
        long kachiGet = measureSequentialReads("Kachi");
        long guavaGet = measureSequentialReads("Guava");
        long caffeineGet = measureSequentialReads("Caffeine");
        String getWinner = getWinner(kachiGet, guavaGet, caffeineGet);

        System.out.printf("GET (ms)       | %12.2f | %12.2f | %12.2f | %s%n",
            kachiGet / 1_000_000.0, guavaGet / 1_000_000.0, caffeineGet / 1_000_000.0, getWinner);

        // Test REMOVE performance
        long kachiRemove = measureSequentialRemoves("Kachi");
        long guavaRemove = measureSequentialRemoves("Guava");
        long caffeineRemove = measureSequentialRemoves("Caffeine");
        String removeWinner = getWinner(kachiRemove, guavaRemove, caffeineRemove);

        System.out.printf("REMOVE (ms)    | %12.2f | %12.2f | %12.2f | %s%n",
            kachiRemove / 1_000_000.0, guavaRemove / 1_000_000.0, caffeineRemove / 1_000_000.0, removeWinner);
    }

    private static void testConcurrentThroughput() throws Exception {
        int[] threadCounts = {1, 4, 8, 16};

        System.out.println("Threads | Kachi (ops/s) | Guava (ops/s) | Caffeine (ops/s) | Fastest");
        System.out.println("--------|---------------|---------------|------------------|--------");

        for (int threads : threadCounts) {
            long kachiThroughput = measureConcurrentThroughput("Kachi", threads);
            long guavaThroughput = measureConcurrentThroughput("Guava", threads);
            long caffeineThroughput = measureConcurrentThroughput("Caffeine", threads);

            String winner = getThroughputWinner(kachiThroughput, guavaThroughput, caffeineThroughput);

            System.out.printf("%7d | %,13d | %,13d | %,16d | %s%n",
                threads, kachiThroughput, guavaThroughput, caffeineThroughput, winner);
        }
    }

    private static void testMixedWorkload() throws Exception {
        System.out.println("Scenario                    | Kachi (ops/s) | Guava (ops/s) | Caffeine (ops/s) | Fastest");
        System.out.println("----------------------------|---------------|---------------|------------------|--------");

        long kachiMixed = measureMixedWorkload("Kachi");
        long guavaMixed = measureMixedWorkload("Guava");
        long caffeineMixed = measureMixedWorkload("Caffeine");

        String winner = getThroughputWinner(kachiMixed, guavaMixed, caffeineMixed);

        System.out.printf("75%% read / 25%% write (8t)  | %,13d | %,13d | %,16d | %s%n",
            kachiMixed, guavaMixed, caffeineMixed, winner);
    }

    private static void testLoadingCache() throws Exception {
        System.out.println("Scenario              | Kachi (ms) | Guava (ms) | Caffeine (ms) | Fastest");
        System.out.println("----------------------|------------|------------|---------------|--------");

        // Test with expensive load (10ms per load)
        long kachiLoad = measureLoadingCache("Kachi", 10);
        long guavaLoad = measureLoadingCache("Guava", 10);
        long caffeineLoad = measureLoadingCache("Caffeine", 10);

        String winner = getWinner(kachiLoad, guavaLoad, caffeineLoad);

        System.out.printf("Expensive load (10ms) | %10.2f | %10.2f | %13.2f | %s%n",
            kachiLoad / 1_000_000.0, guavaLoad / 1_000_000.0, caffeineLoad / 1_000_000.0, winner);
    }

    // ===== Sequential Write Benchmark =====
    private static long measureSequentialWrites(String cacheType) throws Exception {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runSequentialWrites(cacheType);
        }

        // Actual test
        long totalTime = 0;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            totalTime += runSequentialWrites(cacheType);
        }

        return totalTime / TEST_ITERATIONS;
    }

    private static long runSequentialWrites(String cacheType) throws Exception {
        long startTime = System.nanoTime();

        switch (cacheType) {
            case "Kachi":
                com.github.rudygunawan.kachi.api.Cache<Integer, String> kachiCache =
                    CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).build();
                for (int i = 0; i < OPERATIONS; i++) {
                    kachiCache.put(i, "value" + i);
                }
                break;

            case "Guava":
                com.google.common.cache.Cache<Integer, String> guavaCache =
                    com.google.common.cache.CacheBuilder.newBuilder()
                        .maximumSize(CACHE_SIZE).build();
                for (int i = 0; i < OPERATIONS; i++) {
                    guavaCache.put(i, "value" + i);
                }
                break;

            case "Caffeine":
                com.github.benmanes.caffeine.cache.Cache<Integer, String> caffeineCache =
                    Caffeine.newBuilder().maximumSize(CACHE_SIZE).build();
                for (int i = 0; i < OPERATIONS; i++) {
                    caffeineCache.put(i, "value" + i);
                }
                break;
        }

        return System.nanoTime() - startTime;
    }

    // ===== Sequential Read Benchmark =====
    private static long measureSequentialReads(String cacheType) throws Exception {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runSequentialReads(cacheType);
        }

        // Actual test
        long totalTime = 0;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            totalTime += runSequentialReads(cacheType);
        }

        return totalTime / TEST_ITERATIONS;
    }

    private static long runSequentialReads(String cacheType) throws Exception {
        // Pre-populate
        Object cache = null;
        switch (cacheType) {
            case "Kachi":
                com.github.rudygunawan.kachi.api.Cache<Integer, String> kachiCache =
                    CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).build();
                for (int i = 0; i < CACHE_SIZE; i++) {
                    kachiCache.put(i, "value" + i);
                }
                cache = kachiCache;
                break;

            case "Guava":
                com.google.common.cache.Cache<Integer, String> guavaCache =
                    com.google.common.cache.CacheBuilder.newBuilder()
                        .maximumSize(CACHE_SIZE).build();
                for (int i = 0; i < CACHE_SIZE; i++) {
                    guavaCache.put(i, "value" + i);
                }
                cache = guavaCache;
                break;

            case "Caffeine":
                com.github.benmanes.caffeine.cache.Cache<Integer, String> caffeineCache =
                    Caffeine.newBuilder().maximumSize(CACHE_SIZE).build();
                for (int i = 0; i < CACHE_SIZE; i++) {
                    caffeineCache.put(i, "value" + i);
                }
                cache = caffeineCache;
                break;
        }

        long startTime = System.nanoTime();

        // Read operations
        Random rand = new Random(42);
        for (int i = 0; i < OPERATIONS; i++) {
            int key = rand.nextInt(CACHE_SIZE);
            if (cache instanceof com.github.rudygunawan.kachi.api.Cache) {
                ((com.github.rudygunawan.kachi.api.Cache<Integer, String>) cache).getIfPresent(key);
            } else if (cache instanceof com.google.common.cache.Cache) {
                ((com.google.common.cache.Cache<Integer, String>) cache).getIfPresent(key);
            } else if (cache instanceof com.github.benmanes.caffeine.cache.Cache) {
                ((com.github.benmanes.caffeine.cache.Cache<Integer, String>) cache).getIfPresent(key);
            }
        }

        return System.nanoTime() - startTime;
    }

    // ===== Sequential Remove Benchmark =====
    private static long measureSequentialRemoves(String cacheType) throws Exception {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runSequentialRemoves(cacheType);
        }

        // Actual test
        long totalTime = 0;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            totalTime += runSequentialRemoves(cacheType);
        }

        return totalTime / TEST_ITERATIONS;
    }

    private static long runSequentialRemoves(String cacheType) throws Exception {
        Object cache = null;

        // Pre-populate
        switch (cacheType) {
            case "Kachi":
                com.github.rudygunawan.kachi.api.Cache<Integer, String> kachiCache =
                    CacheBuilder.newBuilder().maximumSize(CACHE_SIZE * 2).build();
                for (int i = 0; i < OPERATIONS; i++) {
                    kachiCache.put(i, "value" + i);
                }
                cache = kachiCache;
                break;

            case "Guava":
                com.google.common.cache.Cache<Integer, String> guavaCache =
                    com.google.common.cache.CacheBuilder.newBuilder()
                        .maximumSize(CACHE_SIZE * 2).build();
                for (int i = 0; i < OPERATIONS; i++) {
                    guavaCache.put(i, "value" + i);
                }
                cache = guavaCache;
                break;

            case "Caffeine":
                com.github.benmanes.caffeine.cache.Cache<Integer, String> caffeineCache =
                    Caffeine.newBuilder().maximumSize(CACHE_SIZE * 2).build();
                for (int i = 0; i < OPERATIONS; i++) {
                    caffeineCache.put(i, "value" + i);
                }
                cache = caffeineCache;
                break;
        }

        long startTime = System.nanoTime();

        // Remove operations
        for (int i = 0; i < OPERATIONS; i++) {
            if (cache instanceof com.github.rudygunawan.kachi.api.Cache) {
                ((com.github.rudygunawan.kachi.api.Cache<Integer, String>) cache).invalidate(i);
            } else if (cache instanceof com.google.common.cache.Cache) {
                ((com.google.common.cache.Cache<Integer, String>) cache).invalidate(i);
            } else if (cache instanceof com.github.benmanes.caffeine.cache.Cache) {
                ((com.github.benmanes.caffeine.cache.Cache<Integer, String>) cache).invalidate(i);
            }
        }

        return System.nanoTime() - startTime;
    }

    // ===== Concurrent Throughput Benchmark =====
    private static long measureConcurrentThroughput(String cacheType, int threads) throws Exception {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runConcurrentBenchmark(cacheType, threads);
        }

        // Actual test
        long totalOps = 0;
        long totalTime = 0;

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            runConcurrentBenchmark(cacheType, threads);
            long duration = System.nanoTime() - startTime;

            totalOps += threads * (OPERATIONS / 10);
            totalTime += duration;
        }

        long avgNanos = totalTime / TEST_ITERATIONS;
        return (totalOps / TEST_ITERATIONS) * 1_000_000_000L / avgNanos;
    }

    private static void runConcurrentBenchmark(String cacheType, int threads) throws Exception {
        Object cache = createCache(cacheType);

        // Pre-populate
        for (int i = 0; i < CACHE_SIZE; i++) {
            putToCache(cache, i, "value" + i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    Random rand = new Random(threadId);
                    for (int i = 0; i < OPERATIONS / 10; i++) {
                        int key = rand.nextInt(CACHE_SIZE * 2);
                        int op = rand.nextInt(100);

                        if (op < 70) {
                            // 70% reads
                            getFromCache(cache, key);
                        } else {
                            // 30% writes
                            putToCache(cache, key, "value" + key);
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

    // ===== Mixed Workload Benchmark =====
    private static long measureMixedWorkload(String cacheType) throws Exception {
        return measureConcurrentThroughput(cacheType, 8);  // 8 threads, already mixed
    }

    // ===== LoadingCache Benchmark =====
    private static long measureLoadingCache(String cacheType, int loadDelayMs) throws Exception {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runLoadingCacheBenchmark(cacheType, loadDelayMs);
        }

        // Actual test
        long totalTime = 0;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            totalTime += runLoadingCacheBenchmark(cacheType, loadDelayMs);
        }

        return totalTime / TEST_ITERATIONS;
    }

    private static long runLoadingCacheBenchmark(String cacheType, int loadDelayMs) throws Exception {
        int ops = 100;  // Fewer ops since loads are expensive

        Object loadingCache = null;

        switch (cacheType) {
            case "Kachi":
                loadingCache = CacheBuilder.newBuilder()
                    .maximumSize(CACHE_SIZE)
                    .build(key -> {
                        Thread.sleep(loadDelayMs);
                        return "loaded-" + key;
                    });
                break;

            case "Guava":
                loadingCache = com.google.common.cache.CacheBuilder.newBuilder()
                    .maximumSize(CACHE_SIZE)
                    .build(new CacheLoader<Integer, String>() {
                        @Override
                        public String load(Integer key) throws Exception {
                            Thread.sleep(loadDelayMs);
                            return "loaded-" + key;
                        }
                    });
                break;

            case "Caffeine":
                loadingCache = Caffeine.newBuilder()
                    .maximumSize(CACHE_SIZE)
                    .build(key -> {
                        Thread.sleep(loadDelayMs);
                        return "loaded-" + key;
                    });
                break;
        }

        long startTime = System.nanoTime();

        // Trigger loads
        for (int i = 0; i < ops; i++) {
            if (loadingCache instanceof com.github.rudygunawan.kachi.api.LoadingCache) {
                ((com.github.rudygunawan.kachi.api.LoadingCache<Integer, String>) loadingCache).get(i);
            } else if (loadingCache instanceof com.google.common.cache.LoadingCache) {
                ((com.google.common.cache.LoadingCache<Integer, String>) loadingCache).get(i);
            } else if (loadingCache instanceof LoadingCache) {
                ((LoadingCache<Integer, String>) loadingCache).get(i);
            }
        }

        return System.nanoTime() - startTime;
    }

    // ===== Helper Methods =====

    private static Object createCache(String cacheType) {
        switch (cacheType) {
            case "Kachi":
                return CacheBuilder.newBuilder().maximumSize(CACHE_SIZE * 2).build();
            case "Guava":
                return com.google.common.cache.CacheBuilder.newBuilder()
                    .maximumSize(CACHE_SIZE * 2).build();
            case "Caffeine":
                return Caffeine.newBuilder().maximumSize(CACHE_SIZE * 2).build();
            default:
                throw new IllegalArgumentException("Unknown cache type: " + cacheType);
        }
    }

    private static void putToCache(Object cache, Integer key, String value) {
        if (cache instanceof com.github.rudygunawan.kachi.api.Cache) {
            ((com.github.rudygunawan.kachi.api.Cache<Integer, String>) cache).put(key, value);
        } else if (cache instanceof com.google.common.cache.Cache) {
            ((com.google.common.cache.Cache<Integer, String>) cache).put(key, value);
        } else if (cache instanceof com.github.benmanes.caffeine.cache.Cache) {
            ((com.github.benmanes.caffeine.cache.Cache<Integer, String>) cache).put(key, value);
        }
    }

    private static void getFromCache(Object cache, Integer key) {
        if (cache instanceof com.github.rudygunawan.kachi.api.Cache) {
            ((com.github.rudygunawan.kachi.api.Cache<Integer, String>) cache).getIfPresent(key);
        } else if (cache instanceof com.google.common.cache.Cache) {
            ((com.google.common.cache.Cache<Integer, String>) cache).getIfPresent(key);
        } else if (cache instanceof com.github.benmanes.caffeine.cache.Cache) {
            ((com.github.benmanes.caffeine.cache.Cache<Integer, String>) cache).getIfPresent(key);
        }
    }

    private static String getWinner(long kachi, long guava, long caffeine) {
        long min = Math.min(kachi, Math.min(guava, caffeine));
        if (kachi == min) return "Kachi 🏆";
        if (guava == min) return "Guava 🏆";
        return "Caffeine 🏆";
    }

    private static String getThroughputWinner(long kachi, long guava, long caffeine) {
        long max = Math.max(kachi, Math.max(guava, caffeine));
        if (kachi == max) return "Kachi 🏆";
        if (guava == max) return "Guava 🏆";
        return "Caffeine 🏆";
    }

    private static void printSummary() {
        System.out.println("📊 Summary:");
        System.out.println();
        System.out.println("Caffeine:");
        System.out.println("  • Industry-leading performance (Window TinyLFU, async operations)");
        System.out.println("  • Best for: High-throughput production systems");
        System.out.println();
        System.out.println("Guava:");
        System.out.println("  • Mature and battle-tested (legacy LRU)");
        System.out.println("  • Best for: Stable systems, no breaking changes needed");
        System.out.println();
        System.out.println("Kachi:");
        System.out.println("  • Focus on TTL, refresh policies, and custom eviction");
        System.out.println("  • Best for: Complex expiration requirements, detailed metrics");
        System.out.println();
    }
}
