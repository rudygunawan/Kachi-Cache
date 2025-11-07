package com.github.rudygunawan.kachi.demo;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.CacheStrategy;
import com.github.rudygunawan.kachi.builder.CacheBuilder;

/**
 * Demonstrates how EASY it is to switch between High Performance and Precision caches.
 *
 * SAME CODE, just change ONE line!
 */
public class EasySwitchDemo {

    public static void main(String[] args) {
        System.out.println("════════════════════════════════════════════════════");
        System.out.println("  Kachi Dual Implementation Demo");
        System.out.println("  Switch cache strategy with ONE line!");
        System.out.println("════════════════════════════════════════════════════\n");

        // Demo 1: High Performance (default)
        System.out.println("📊 Demo 1: High Performance Cache (Default)");
        System.out.println("─".repeat(50));
        demoCache("HIGH_PERFORMANCE", CacheStrategy.HIGH_PERFORMANCE);

        System.out.println("\n📊 Demo 2: Precision Cache");
        System.out.println("─".repeat(50));
        demoCache("PRECISION", CacheStrategy.PRECISION);

        System.out.println("\n📊 Demo 3: Easy Switching");
        System.out.println("─".repeat(50));
        showEasySwitching();

        System.out.println("\n✅ All demos completed!");
    }

    private static void demoCache(String name, CacheStrategy strategy) {
        // Build cache with strategy
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .strategy(strategy)  // ← ONLY LINE THAT CHANGES!
                .maximumSize(1000)
                .expireAfterWrite(5, java.util.concurrent.TimeUnit.MINUTES)
                .recordStats()
                .build();

        // Use the cache (same API!)
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        System.out.println("Strategy: " + name);
        System.out.println("get('key1'): " + cache.getIfPresent("key1"));
        System.out.println("get('key2'): " + cache.getIfPresent("key2"));
        System.out.println("Size: " + cache.size());
        System.out.println("Implementation: " + cache.getClass().getSimpleName());
    }

    private static void showEasySwitching() {
        System.out.println("Code Example - Switch with ONE line:\n");

        String code = """
                // Option 1: Fast (default)
                var cache = CacheBuilder.newBuilder()
                    .strategy(CacheStrategy.HIGH_PERFORMANCE)  // ← THIS LINE
                    .maximumSize(10000)
                    .build();

                // Option 2: Accurate
                var cache = CacheBuilder.newBuilder()
                    .strategy(CacheStrategy.PRECISION)  // ← JUST CHANGE THIS!
                    .maximumSize(10000)
                    .build();

                // Everything else is THE SAME!
                cache.put(key, value);
                cache.getIfPresent(key);
                cache.invalidate(key);
                """;

        System.out.println(code);
        System.out.println("✅ SAME API, different performance characteristics!");
    }
}
