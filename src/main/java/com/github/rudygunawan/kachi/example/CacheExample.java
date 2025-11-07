package com.github.rudygunawan.kachi.example;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.CacheLoader;
import com.github.rudygunawan.kachi.api.LoadingCache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.listener.RemovalListener;
import com.github.rudygunawan.kachi.model.CacheStats;
import com.github.rudygunawan.kachi.policy.EvictionPolicy;
import com.github.rudygunawan.kachi.policy.RemovalCause;

import java.util.concurrent.TimeUnit;

/**
 * Example usage of Kachi Cache library.
 */
public class CacheExample {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Kachi Cache Examples ===\n");

        // Example 1: Basic Cache
        basicCacheExample();

        // Example 2: Loading Cache
        loadingCacheExample();

        // Example 3: TTL Cache
        ttlCacheExample();

        // Example 4: Size-Limited Cache with LRU
        lruCacheExample();

        // Example 5: Cache with Statistics
        statisticsExample();
    }

    private static void basicCacheExample() {
        System.out.println("1. Basic Cache Example");
        System.out.println("----------------------");

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .build();

        cache.put("user:123", "John Doe");
        cache.put("user:456", "Jane Smith");

        System.out.println("user:123 = " + cache.getIfPresent("user:123"));
        System.out.println("user:999 = " + cache.getIfPresent("user:999"));
        System.out.println("Cache size: " + cache.size());
        System.out.println();
    }

    private static void loadingCacheExample() throws Exception {
        System.out.println("2. Loading Cache Example (Database Simulation)");
        System.out.println("----------------------------------------------");

        // Simulate database access
        CacheLoader<Integer, String> loader = new CacheLoader<Integer, String>() {
            @Override
            public String load(Integer id) throws Exception {
                System.out.println("  Loading user " + id + " from database...");
                Thread.sleep(100); // Simulate database latency
                return "User#" + id;
            }
        };

        LoadingCache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .recordStats()
                .build(loader);

        // First access - loads from "database"
        System.out.println("First get(1): " + cache.get(1));

        // Second access - returns cached value
        System.out.println("Second get(1): " + cache.get(1));

        // New key - loads from "database"
        System.out.println("First get(2): " + cache.get(2));

        CacheStats stats = cache.stats();
        System.out.println("Hit rate: " + String.format("%.2f%%", stats.hitRate() * 100));
        System.out.println("Loads: " + stats.loadCount());
        System.out.println();
    }

    private static void ttlCacheExample() throws Exception {
        System.out.println("3. TTL (Time-To-Live) Cache Example");
        System.out.println("-----------------------------------");

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(2, TimeUnit.SECONDS)
                .build();

        cache.put("session:abc", "active");
        System.out.println("Immediately: session:abc = " + cache.getIfPresent("session:abc"));

        System.out.println("Waiting 1 second...");
        Thread.sleep(1000);
        System.out.println("After 1 second: session:abc = " + cache.getIfPresent("session:abc"));

        System.out.println("Waiting 2 more seconds...");
        Thread.sleep(2000);
        System.out.println("After 3 seconds total: session:abc = " + cache.getIfPresent("session:abc"));
        System.out.println();
    }

    private static void lruCacheExample() {
        System.out.println("4. LRU Cache Example (Max Size = 3)");
        System.out.println("-----------------------------------");

        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(3)
                .recordStats()
                .build();

        cache.put(1, "One");
        cache.put(2, "Two");
        cache.put(3, "Three");
        System.out.println("Added 1, 2, 3. Size: " + cache.size());

        // Access 1 to make it recently used
        cache.getIfPresent(1);
        System.out.println("Accessed key 1");

        // Add 4 - should evict 2 (least recently used)
        cache.put(4, "Four");
        System.out.println("Added 4. Size: " + cache.size());

        System.out.println("Key 1 present: " + (cache.getIfPresent(1) != null));
        System.out.println("Key 2 present: " + (cache.getIfPresent(2) != null) + " (evicted)");
        System.out.println("Key 3 present: " + (cache.getIfPresent(3) != null));
        System.out.println("Key 4 present: " + (cache.getIfPresent(4) != null));
        System.out.println("Evictions: " + cache.stats().evictionCount());
        System.out.println();
    }

    private static void statisticsExample() throws Exception {
        System.out.println("5. Cache Statistics Example");
        System.out.println("--------------------------");

        LoadingCache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(10)
                .recordStats()
                .build(new CacheLoader<String, Integer>() {
                    @Override
                    public Integer load(String key) {
                        return key.length();
                    }
                });

        // Perform various operations
        cache.get("hello");
        cache.get("world");
        cache.get("hello"); // Hit
        cache.get("java");
        cache.get("world"); // Hit

        CacheStats stats = cache.stats();
        System.out.println("Total requests: " + stats.requestCount());
        System.out.println("Hits: " + stats.hitCount());
        System.out.println("Misses: " + stats.missCount());
        System.out.println("Hit rate: " + String.format("%.2f%%", stats.hitRate() * 100));
        System.out.println("Loads: " + stats.loadCount());
        System.out.println("Load successes: " + stats.loadSuccessCount());
        System.out.println("Load failures: " + stats.loadFailureCount());
        System.out.println();
    }
}
