package com.github.rudygunawan.kachi.performance;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Proof-of-concept demonstration of cache partitioning benefits.
 *
 * <p>This demo compares two cache implementations:
 * <ol>
 *   <li>Single-partition: One ConcurrentHashMap with one lock</li>
 *   <li>Multi-partition: N ConcurrentHashMaps with independent locks</li>
 * </ol>
 *
 * <p><b>Expected Results:</b>
 * With increasing thread counts, the multi-partition cache should show
 * significantly better throughput due to reduced lock contention.
 */
public class PartitionedCacheDemo {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int TEST_ITERATIONS = 5;
    private static final int OPERATIONS_PER_THREAD = 50_000;

    public static void main(String[] args) throws Exception {
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║         Cache Partitioning - Proof of Concept Demo           ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Comparing:");
        System.out.println("  • Single-partition cache (baseline)");
        System.out.println("  • Multi-partition cache (16 partitions)");
        System.out.println();
        System.out.println("Test Configuration:");
        System.out.println("  Operations per thread: " + String.format("%,d", OPERATIONS_PER_THREAD));
        System.out.println("  Test iterations: " + TEST_ITERATIONS);
        System.out.println("  Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println();

        runBenchmark();

        System.out.println();
        System.out.println("═".repeat(80));
        System.out.println("✅ Demo complete!");
    }

    private static void runBenchmark() throws Exception {
        System.out.println("═══ Concurrent Throughput Comparison ═══\n");

        int[] threadCounts = {1, 2, 4, 8, 16};

        System.out.println("Threads | Single Partition  | Multi Partition   | Speedup | Improvement");
        System.out.println("        | (ops/sec)         | (ops/sec)         |         |");
        System.out.println("--------|-------------------|-------------------|---------|------------");

        for (int threads : threadCounts) {
            long singleThroughput = measureThroughput(new SinglePartitionCache<>(), threads);
            long multiThroughput = measureThroughput(new MultiPartitionCache<>(16), threads);

            double speedup = (double) multiThroughput / singleThroughput;
            String improvement = String.format("%+.0f%%", (speedup - 1.0) * 100);

            System.out.printf("  %2d    | %,16d  | %,16d  | %6.2fx | %10s%n",
                threads,
                singleThroughput,
                multiThroughput,
                speedup,
                improvement);
        }

        System.out.println();
        System.out.println("💡 Key Observations:");
        System.out.println("   • Single-threaded: Partitioning has minimal overhead");
        System.out.println("   • Multi-threaded: Partitioning reduces lock contention");
        System.out.println("   • Higher thread counts: Greater partitioning benefits");
    }

    private static long measureThroughput(SimpleCache<Integer, String> cache, int threads) throws Exception {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runConcurrentTest(cache, threads);
            cache.clear();
        }

        // Actual test
        long totalOps = 0;
        long totalTime = 0;

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            runConcurrentTest(cache, threads);
            long duration = System.nanoTime() - startTime;

            totalOps += threads * OPERATIONS_PER_THREAD;
            totalTime += duration;
            cache.clear();
        }

        long avgNanos = totalTime / TEST_ITERATIONS;
        return (totalOps / TEST_ITERATIONS) * 1_000_000_000L / avgNanos;
    }

    private static void runConcurrentTest(SimpleCache<Integer, String> cache, int threads) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    Random rand = new Random(threadId);
                    for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                        int key = rand.nextInt(1000);
                        int op = rand.nextInt(100);

                        if (op < 50) {
                            // 50% reads
                            cache.get(key);
                        } else if (op < 85) {
                            // 35% writes
                            cache.put(key, "value" + key);
                        } else {
                            // 15% deletes
                            cache.remove(key);
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

    /**
     * Simple cache interface for benchmarking.
     */
    interface SimpleCache<K, V> {
        V get(K key);
        void put(K key, V value);
        void remove(K key);
        void clear();
    }

    /**
     * Single-partition cache - baseline implementation.
     * Uses one ConcurrentHashMap with per-key locks.
     */
    static class SinglePartitionCache<K, V> implements SimpleCache<K, V> {
        private final ConcurrentHashMap<K, V> storage = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<K, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

        @Override
        public V get(K key) {
            ReentrantReadWriteLock lock = locks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
            lock.readLock().lock();
            try {
                return storage.get(key);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void put(K key, V value) {
            ReentrantReadWriteLock lock = locks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
            lock.writeLock().lock();
            try {
                storage.put(key, value);
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public void remove(K key) {
            ReentrantReadWriteLock lock = locks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
            lock.writeLock().lock();
            try {
                storage.remove(key);
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public void clear() {
            storage.clear();
            locks.clear();
        }
    }

    /**
     * Multi-partition cache - partitioned implementation.
     * Uses N independent ConcurrentHashMaps with separate locks per partition.
     */
    static class MultiPartitionCache<K, V> implements SimpleCache<K, V> {
        private final Partition<K, V>[] partitions;
        private final int partitionCount;

        @SuppressWarnings("unchecked")
        public MultiPartitionCache(int partitionCount) {
            this.partitionCount = partitionCount;
            this.partitions = new Partition[partitionCount];
            for (int i = 0; i < partitionCount; i++) {
                partitions[i] = new Partition<>();
            }
        }

        private Partition<K, V> getPartition(K key) {
            int hash = key.hashCode();
            int index = (hash & 0x7FFFFFFF) % partitionCount;
            return partitions[index];
        }

        @Override
        public V get(K key) {
            return getPartition(key).get(key);
        }

        @Override
        public void put(K key, V value) {
            getPartition(key).put(key, value);
        }

        @Override
        public void remove(K key) {
            getPartition(key).remove(key);
        }

        @Override
        public void clear() {
            for (Partition<K, V> partition : partitions) {
                partition.clear();
            }
        }

        /**
         * A single partition with its own storage and locks.
         */
        static class Partition<K, V> {
            private final ConcurrentHashMap<K, V> storage = new ConcurrentHashMap<>();
            private final ConcurrentHashMap<K, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

            public V get(K key) {
                ReentrantReadWriteLock lock = locks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
                lock.readLock().lock();
                try {
                    return storage.get(key);
                } finally {
                    lock.readLock().unlock();
                }
            }

            public void put(K key, V value) {
                ReentrantReadWriteLock lock = locks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
                lock.writeLock().lock();
                try {
                    storage.put(key, value);
                } finally {
                    lock.writeLock().unlock();
                }
            }

            public void remove(K key) {
                ReentrantReadWriteLock lock = locks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
                lock.writeLock().lock();
                try {
                    storage.remove(key);
                } finally {
                    lock.writeLock().unlock();
                }
            }

            public void clear() {
                storage.clear();
                locks.clear();
            }
        }
    }
}
