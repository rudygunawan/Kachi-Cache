package com.github.rudygunawan.kachi.example;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.impl.ConcurrentCacheImpl;
import com.github.rudygunawan.kachi.metrics.ExpiryDistribution;

import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating the new metrics features:
 * - Expiry distribution statistics
 * - Average entry size
 * - Cache size in MB/GB
 */
public class MetricsExample {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Kachi Cache Metrics Example ===\n");

        // Create a cache with TTL
        Cache<String, String> cacheInterface = CacheBuilder.newBuilder()
                .<String, String>maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
        @SuppressWarnings("unchecked")
        ConcurrentCacheImpl<String, String> cache = (ConcurrentCacheImpl<String, String>) cacheInterface;

        // Add some entries
        System.out.println("Adding 100 entries to cache...");
        for (int i = 0; i < 100; i++) {
            cache.put("key" + i, "value" + i);
        }

        // Display basic metrics
        System.out.println("\n=== Basic Metrics ===");
        System.out.println("Cache size: " + cache.size() + " entries");
        System.out.println("Estimated memory: " + cache.estimatedMemoryUsageBytes() + " bytes");
        System.out.println("Average entry size: " + cache.averageEntrySizeBytes() + " bytes");
        System.out.println("Cache size (MB): " + String.format("%.3f", cache.cacheSizeMB()) + " MB");
        System.out.println("Cache size (GB): " + String.format("%.6f", cache.cacheSizeGB()) + " GB");

        // Display expiry distribution
        System.out.println("\n=== Expiry Distribution ===");
        ExpiryDistribution distribution = cache.expiryDistribution();
        System.out.println(distribution);

        // Create another cache with different TTL settings
        System.out.println("\n=== Cache with Mixed TTL ===");
        Cache<String, String> mixedCacheInterface = CacheBuilder.newBuilder()
                .<String, String>maximumSize(1000)
                .recordStats()
                .build();
        @SuppressWarnings("unchecked")
        ConcurrentCacheImpl<String, String> mixedCache = (ConcurrentCacheImpl<String, String>) mixedCacheInterface;

        // Add entries that will expire at different times
        System.out.println("Adding entries with various expiry times...");

        // Create a cache with 1 minute TTL
        Cache<String, String> shortTtlCache = CacheBuilder.newBuilder()
                .<String, String>expireAfterWrite(1, TimeUnit.MINUTES)
                .build();

        // Create a cache with 30 minutes TTL
        Cache<String, String> mediumTtlCache = CacheBuilder.newBuilder()
                .<String, String>expireAfterWrite(30, TimeUnit.MINUTES)
                .build();

        // Create a cache with 2 hours TTL
        Cache<String, String> longTtlCache = CacheBuilder.newBuilder()
                .<String, String>expireAfterWrite(2, TimeUnit.HOURS)
                .build();

        // Add entries to each
        for (int i = 0; i < 20; i++) {
            shortTtlCache.put("short" + i, "value" + i);
        }
        for (int i = 0; i < 50; i++) {
            mediumTtlCache.put("medium" + i, "value" + i);
        }
        for (int i = 0; i < 30; i++) {
            longTtlCache.put("long" + i, "value" + i);
        }

        // Display metrics for each cache
        System.out.println("\n=== Short TTL Cache (1 minute) ===");
        if (shortTtlCache instanceof ConcurrentCacheImpl) {
            ConcurrentCacheImpl<String, String> impl = (ConcurrentCacheImpl<String, String>) shortTtlCache;
            System.out.println("Size: " + impl.size() + " entries");
            System.out.println("Memory: " + String.format("%.3f", impl.cacheSizeMB()) + " MB");
            System.out.println(impl.expiryDistribution());
        }

        System.out.println("=== Medium TTL Cache (30 minutes) ===");
        if (mediumTtlCache instanceof ConcurrentCacheImpl) {
            ConcurrentCacheImpl<String, String> impl = (ConcurrentCacheImpl<String, String>) mediumTtlCache;
            System.out.println("Size: " + impl.size() + " entries");
            System.out.println("Memory: " + String.format("%.3f", impl.cacheSizeMB()) + " MB");
            System.out.println(impl.expiryDistribution());
        }

        System.out.println("=== Long TTL Cache (2 hours) ===");
        if (longTtlCache instanceof ConcurrentCacheImpl) {
            ConcurrentCacheImpl<String, String> impl = (ConcurrentCacheImpl<String, String>) longTtlCache;
            System.out.println("Size: " + impl.size() + " entries");
            System.out.println("Memory: " + String.format("%.3f", impl.cacheSizeMB()) + " MB");
            System.out.println(impl.expiryDistribution());
        }

        System.out.println("\n=== Example Complete ===");
    }
}
