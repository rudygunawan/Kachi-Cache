package com.github.rudygunawan.kachi.example;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.CacheLoader;
import com.github.rudygunawan.kachi.api.LoadingCache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Example demonstrating optimized bulk operations in Kachi Cache.
 *
 * <p>Bulk operations are more efficient than individual operations when working with
 * multiple keys because they:
 * <ul>
 *   <li>Reduce method call overhead
 *   <li>Enable parallel processing for loads
 *   <li>Batch lock acquisitions
 *   <li>Optimize memory allocations
 * </ul>
 */
public class BulkOperationsExample {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Kachi Cache Bulk Operations Examples ===\n");

        // Example 1: getAllPresent - retrieve multiple entries without loading
        example1_GetAllPresent();

        System.out.println("\n" + "=".repeat(80) + "\n");

        // Example 2: putAll - insert multiple entries efficiently
        example2_PutAll();

        System.out.println("\n" + "=".repeat(80) + "\n");

        // Example 3: invalidateAll - remove multiple entries efficiently
        example3_InvalidateAll();

        System.out.println("\n" + "=".repeat(80) + "\n");

        // Example 4: getAll with parallel loading
        example4_GetAllParallel();

        System.out.println("\n" + "=".repeat(80) + "\n");

        // Example 5: Performance comparison
        example5_PerformanceComparison();

        System.exit(0);
    }

    /**
     * Example 1: getAllPresent - retrieve multiple cached entries without loading.
     */
    private static void example1_GetAllPresent() {
        System.out.println("=== Example 1: getAllPresent ===");
        System.out.println("Retrieve multiple cached entries efficiently (no loading)\n");

        Cache<String, User> cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .recordStats()
                .build();

        // Populate cache
        System.out.println("Populating cache with 5 users:");
        for (int i = 1; i <= 5; i++) {
            User user = new User("user" + i, "User " + i);
            cache.put("user" + i, user);
            System.out.println("  Added: " + user.getName());
        }

        // Retrieve subset using bulk operation
        List<String> keysToGet = Arrays.asList("user1", "user3", "user5", "user999");
        System.out.println("\nRetrieving keys: " + keysToGet);

        Map<String, User> result = cache.getAllPresent(keysToGet);
        System.out.println("\nResults:");
        System.out.println("  Found " + result.size() + " out of " + keysToGet.size() + " keys");
        for (Map.Entry<String, User> entry : result.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue().getName());
        }
        System.out.println("  user999 was not in cache (not loaded)");

        System.out.println("\nStats:");
        System.out.println("  Hit rate: " + String.format("%.2f%%", cache.stats().hitRate() * 100));
    }

    /**
     * Example 2: putAll - insert multiple entries efficiently.
     */
    private static void example2_PutAll() {
        System.out.println("=== Example 2: putAll ===");
        System.out.println("Insert multiple entries in a single batch operation\n");

        Cache<String, User> cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .build();

        // Prepare batch data
        Map<String, User> batchData = new HashMap<>();
        for (int i = 1; i <= 100; i++) {
            batchData.put("user" + i, new User("user" + i, "User " + i));
        }

        System.out.println("Inserting " + batchData.size() + " users using putAll()...");
        long start = System.nanoTime();
        cache.putAll(batchData);
        long duration = System.nanoTime() - start;

        System.out.println("\nResults:");
        System.out.println("  Inserted: " + batchData.size() + " entries");
        System.out.println("  Cache size: " + cache.size());
        System.out.println("  Time taken: " + duration / 1_000_000 + " ms");
        System.out.println("  Average per entry: " + duration / batchData.size() + " ns");

        // Verify some entries
        System.out.println("\nVerifying random entries:");
        for (String key : Arrays.asList("user1", "user50", "user100")) {
            User user = cache.getIfPresent(key);
            System.out.println("  " + key + " -> " + (user != null ? user.getName() : "NOT FOUND"));
        }
    }

    /**
     * Example 3: invalidateAll - remove multiple entries efficiently.
     */
    private static void example3_InvalidateAll() {
        System.out.println("=== Example 3: invalidateAll ===");
        System.out.println("Remove multiple entries efficiently\n");

        Cache<String, User> cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .build();

        // Populate cache
        System.out.println("Populating cache with 100 users...");
        for (int i = 1; i <= 100; i++) {
            cache.put("user" + i, new User("user" + i, "User " + i));
        }
        System.out.println("Initial cache size: " + cache.size());

        // Remove odd-numbered users using bulk operation
        List<String> keysToRemove = new ArrayList<>();
        for (int i = 1; i <= 100; i += 2) {
            keysToRemove.add("user" + i);
        }

        System.out.println("\nRemoving " + keysToRemove.size() + " entries using invalidateAll()...");
        long start = System.nanoTime();
        cache.invalidateAll(keysToRemove);
        long duration = System.nanoTime() - start;

        System.out.println("\nResults:");
        System.out.println("  Removed: " + keysToRemove.size() + " entries");
        System.out.println("  Final cache size: " + cache.size());
        System.out.println("  Time taken: " + duration / 1_000_000 + " ms");
        System.out.println("  Average per entry: " + duration / keysToRemove.size() + " ns");

        // Verify removals
        System.out.println("\nVerifying removals:");
        System.out.println("  user1 (should be removed): " + (cache.getIfPresent("user1") == null ? "✓" : "✗"));
        System.out.println("  user2 (should exist): " + (cache.getIfPresent("user2") != null ? "✓" : "✗"));
        System.out.println("  user51 (should be removed): " + (cache.getIfPresent("user51") == null ? "✓" : "✗"));
        System.out.println("  user52 (should exist): " + (cache.getIfPresent("user52") != null ? "✓" : "✗"));
    }

    /**
     * Example 4: getAll with parallel loading for LoadingCache.
     */
    private static void example4_GetAllParallel() throws Exception {
        System.out.println("=== Example 4: getAll with Parallel Loading ===");
        System.out.println("Load multiple entries in parallel (for LoadingCache)\n");

        // Create loading cache with simulated database
        LoadingCache<Integer, User> cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .recordStats()
                .build(new CacheLoader<Integer, User>() {
                    @Override
                    public User load(Integer id) throws Exception {
                        // Simulate database query (50ms latency)
                        Thread.sleep(50);
                        return new User("user" + id, "User " + id);
                    }
                });

        // Pre-populate some entries
        System.out.println("Pre-populating cache with users 1-3...");
        for (int i = 1; i <= 3; i++) {
            cache.get(i);
        }
        System.out.println("Cache size: " + cache.size());

        // Get multiple entries (some cached, some need loading)
        List<Integer> keysToGet = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        System.out.println("\nLoading " + keysToGet.size() + " users (3 cached, 7 need loading)...");
        System.out.println("Keys: " + keysToGet);

        long start = System.nanoTime();
        Map<Integer, User> result = cache.getAll(keysToGet);
        long duration = System.nanoTime() - start;

        System.out.println("\nResults:");
        System.out.println("  Loaded: " + result.size() + " entries");
        System.out.println("  Time taken: " + duration / 1_000_000 + " ms");
        System.out.println("  Expected sequential time: ~350ms (7 loads × 50ms)");
        System.out.println("  Actual time shows parallel loading benefit!");

        System.out.println("\nStats:");
        System.out.println("  Hit count: " + cache.stats().hitCount());
        System.out.println("  Miss count: " + cache.stats().missCount());
        System.out.println("  Load success count: " + cache.stats().loadSuccessCount());
    }

    /**
     * Example 5: Performance comparison - individual vs bulk operations.
     */
    private static void example5_PerformanceComparison() {
        System.out.println("=== Example 5: Performance Comparison ===");
        System.out.println("Individual operations vs bulk operations\n");

        int numEntries = 1000;

        // Test 1: Individual puts vs putAll
        System.out.println("Test 1: INSERT Performance");
        System.out.println("-".repeat(40));

        // Individual puts
        Cache<String, Integer> cache1 = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .build();

        long start = System.nanoTime();
        for (int i = 0; i < numEntries; i++) {
            cache1.put("key" + i, i);
        }
        long individualPutTime = System.nanoTime() - start;

        // Bulk putAll
        Cache<String, Integer> cache2 = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .build();

        Map<String, Integer> batchData = new HashMap<>();
        for (int i = 0; i < numEntries; i++) {
            batchData.put("key" + i, i);
        }

        start = System.nanoTime();
        cache2.putAll(batchData);
        long bulkPutTime = System.nanoTime() - start;

        System.out.println("Individual put() × " + numEntries + ": " + individualPutTime / 1_000_000 + " ms");
        System.out.println("Bulk putAll():              " + bulkPutTime / 1_000_000 + " ms");
        System.out.println("Speedup:                    " + String.format("%.2fx", (double) individualPutTime / bulkPutTime));

        // Test 2: Individual gets vs getAllPresent
        System.out.println("\nTest 2: RETRIEVAL Performance");
        System.out.println("-".repeat(40));

        Cache<String, Integer> cache3 = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .build();

        // Populate
        for (int i = 0; i < numEntries; i++) {
            cache3.put("key" + i, i);
        }

        List<String> keysToGet = new ArrayList<>();
        for (int i = 0; i < numEntries; i++) {
            keysToGet.add("key" + i);
        }

        // Individual gets
        start = System.nanoTime();
        Map<String, Integer> individualResults = new HashMap<>();
        for (String key : keysToGet) {
            Integer value = cache3.getIfPresent(key);
            if (value != null) {
                individualResults.put(key, value);
            }
        }
        long individualGetTime = System.nanoTime() - start;

        // Bulk getAllPresent
        start = System.nanoTime();
        Map<String, Integer> bulkResults = cache3.getAllPresent(keysToGet);
        long bulkGetTime = System.nanoTime() - start;

        System.out.println("Individual getIfPresent() × " + numEntries + ": " + individualGetTime / 1_000_000 + " ms");
        System.out.println("Bulk getAllPresent():                " + bulkGetTime / 1_000_000 + " ms");
        System.out.println("Speedup:                             " + String.format("%.2fx", (double) individualGetTime / bulkGetTime));

        // Test 3: Individual invalidates vs invalidateAll
        System.out.println("\nTest 3: REMOVAL Performance");
        System.out.println("-".repeat(40));

        Cache<String, Integer> cache4 = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .build();

        // Populate
        for (int i = 0; i < numEntries; i++) {
            cache4.put("key" + i, i);
        }

        List<String> keysToRemove = new ArrayList<>();
        for (int i = 0; i < numEntries / 2; i++) {
            keysToRemove.add("key" + i);
        }

        // Individual invalidates
        Cache<String, Integer> cache5 = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .build();

        for (int i = 0; i < numEntries; i++) {
            cache5.put("key" + i, i);
        }

        start = System.nanoTime();
        for (String key : keysToRemove) {
            cache5.invalidate(key);
        }
        long individualInvalidateTime = System.nanoTime() - start;

        // Bulk invalidateAll
        start = System.nanoTime();
        cache4.invalidateAll(keysToRemove);
        long bulkInvalidateTime = System.nanoTime() - start;

        System.out.println("Individual invalidate() × " + keysToRemove.size() + ": " + individualInvalidateTime / 1_000_000 + " ms");
        System.out.println("Bulk invalidateAll():              " + bulkInvalidateTime / 1_000_000 + " ms");
        System.out.println("Speedup:                           " + String.format("%.2fx", (double) individualInvalidateTime / bulkInvalidateTime));

        System.out.println("\n" + "=".repeat(40));
        System.out.println("Summary: Bulk operations are consistently faster!");
    }

    /**
     * Simple User class for examples.
     */
    private static class User {
        private final String id;
        private final String name;

        public User(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "User{id='" + id + "', name='" + name + "'}";
        }
    }
}
