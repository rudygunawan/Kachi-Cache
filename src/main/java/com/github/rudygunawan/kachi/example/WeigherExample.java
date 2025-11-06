package com.github.rudygunawan.kachi.example;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.Weigher;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.impl.ConcurrentCacheImpl;
import com.github.rudygunawan.kachi.policy.EvictionPolicy;

import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating variable-size cache entries using Weigher for weight-based eviction.
 *
 * <p>By default, caches use maximumSize to limit the number of entries. However, when entries
 * have vastly different sizes (e.g., small strings vs large byte arrays), it's more efficient
 * to evict based on total memory usage rather than entry count.
 *
 * <p>The Weigher interface allows you to specify custom weights for entries, enabling
 * size-aware eviction policies.
 */
public class WeigherExample {

    public static void main(String[] args) {
        System.out.println("=== Kachi Cache Weigher Examples ===\n");

        // Example 1: Byte array cache with size-based eviction
        example1_ByteArrayCache();

        System.out.println("\n" + "=".repeat(80) + "\n");

        // Example 2: String cache with character count weighting
        example2_StringCache();

        System.out.println("\n" + "=".repeat(80) + "\n");

        // Example 3: Comparison - maximumSize vs maximumWeight
        example3_Comparison();

        System.out.println("\n" + "=".repeat(80) + "\n");

        // Example 4: Using built-in weighers
        example4_BuiltInWeighers();

        // Exit cleanly
        System.exit(0);
    }

    /**
     * Example 1: Byte array cache with size-based eviction.
     * Weight = byte array length (approximates memory usage).
     */
    private static void example1_ByteArrayCache() {
        System.out.println("=== Example 1: Byte Array Cache ===");
        System.out.println("Goal: Store byte arrays with total size limit of 10,000 bytes\n");

        // Create cache with maximum weight of 10,000 bytes
        Cache<String, byte[]> cache = CacheBuilder.newBuilder()
                .maximumWeight(10_000)
                .weigher(Weigher.byteArrayWeigher())
                .evictionPolicy(EvictionPolicy.LRU)
                .recordStats()
                .build();

        // Add various sized byte arrays
        System.out.println("Adding entries:");
        cache.put("small-1", new byte[100]);
        System.out.println("  small-1: 100 bytes");

        cache.put("small-2", new byte[200]);
        System.out.println("  small-2: 200 bytes");

        cache.put("medium", new byte[2000]);
        System.out.println("  medium: 2,000 bytes");

        cache.put("large-1", new byte[5000]);
        System.out.println("  large-1: 5,000 bytes");

        // Wait 1.5 seconds so entries become eligible for eviction (min age: 1 second)
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        cache.put("large-2", new byte[5000]);
        System.out.println("  large-2: 5,000 bytes (should trigger eviction)");

        System.out.println("\nFinal cache state:");
        System.out.println("  Size: " + cache.size() + " entries");
        System.out.println("  Evictions: " + cache.stats().evictionCount());

        // Check which entries remain
        System.out.println("\nEntries still in cache:");
        for (String key : new String[]{"small-1", "small-2", "medium", "large-1", "large-2"}) {
            boolean present = cache.getIfPresent(key) != null;
            System.out.println("  " + key + ": " + (present ? "✓ present" : "✗ evicted"));
        }
    }

    /**
     * Example 2: String cache with character count weighting.
     * Weight = sum of key length and value length.
     */
    private static void example2_StringCache() {
        System.out.println("=== Example 2: String Cache ===");
        System.out.println("Goal: Store strings with character count limit of 100\n");

        // Create cache with maximum weight based on string lengths
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumWeight(100)
                .weigher(Weigher.stringWeigher())  // Weight = key.length() + value.length()
                .evictionPolicy(EvictionPolicy.LRU)
                .recordStats()
                .build();

        System.out.println("Adding entries:");
        cache.put("k1", "short");  // 2 + 5 = 7 chars
        System.out.println("  k1='short' (7 chars total)");

        cache.put("k2", "medium length value");  // 2 + 18 = 20 chars
        System.out.println("  k2='medium length value' (20 chars total)");

        cache.put("longer-key", "this is a longer value");  // 10 + 22 = 32 chars
        System.out.println("  longer-key='this is a longer value' (32 chars total)");

        cache.put("k3", "x".repeat(50));  // 2 + 50 = 52 chars (should trigger eviction)
        System.out.println("  k3='x...x' (52 chars total - should trigger eviction)");

        System.out.println("\nFinal cache state:");
        System.out.println("  Size: " + cache.size() + " entries");
        System.out.println("  Evictions: " + cache.stats().evictionCount());

        System.out.println("\nEntries still in cache:");
        for (String key : new String[]{"k1", "k2", "longer-key", "k3"}) {
            String value = cache.getIfPresent(key);
            if (value != null) {
                System.out.println("  " + key + ": ✓ present");
            } else {
                System.out.println("  " + key + ": ✗ evicted");
            }
        }
    }

    /**
     * Example 3: Comparison between maximumSize and maximumWeight.
     * Shows how weight-based eviction is more memory-efficient for variable-size entries.
     */
    private static void example3_Comparison() {
        System.out.println("=== Example 3: maximumSize vs maximumWeight ===");

        // Scenario: Storing 3 entries - two small (100 bytes) and one huge (10,000 bytes)

        // Cache A: Entry count limit (maximumSize = 3)
        System.out.println("\nCache A - maximumSize(3): Limit by entry count");
        Cache<String, byte[]> cacheA = CacheBuilder.newBuilder()
                .maximumSize(3)
                .evictionPolicy(EvictionPolicy.LRU)
                .recordStats()
                .build();

        cacheA.put("small-1", new byte[100]);
        cacheA.put("small-2", new byte[100]);
        cacheA.put("huge", new byte[10_000]);

        System.out.println("  Added: small-1 (100 bytes), small-2 (100 bytes), huge (10,000 bytes)");
        System.out.println("  Entries: " + cacheA.size());
        System.out.println("  Total memory: ~10,200 bytes (all entries fit)");

        // Cache B: Memory limit (maximumWeight = 1000 bytes)
        System.out.println("\nCache B - maximumWeight(1000): Limit by memory usage");
        Cache<String, byte[]> cacheB = CacheBuilder.newBuilder()
                .maximumWeight(1000)
                .weigher(Weigher.byteArrayWeigher())
                .evictionPolicy(EvictionPolicy.LRU)
                .recordStats()
                .build();

        cacheB.put("small-1", new byte[100]);
        cacheB.put("small-2", new byte[100]);
        cacheB.put("huge", new byte[10_000]);  // Exceeds limit, triggers evictions

        System.out.println("  Added: small-1 (100 bytes), small-2 (100 bytes), huge (10,000 bytes)");
        System.out.println("  Entries: " + cacheB.size());
        System.out.println("  Total memory: ~10,000 bytes (huge evicted the small entries)");
        System.out.println("  Evictions: " + cacheB.stats().evictionCount());

        System.out.println("\nConclusion:");
        System.out.println("  - maximumSize: Good for uniform entry sizes");
        System.out.println("  - maximumWeight: Better memory control for variable-size entries");
    }

    /**
     * Example 4: Using built-in weighers from the Weigher interface.
     */
    private static void example4_BuiltInWeighers() {
        System.out.println("=== Example 4: Built-in Weighers ===\n");

        // 1. Singleton weigher (weight = 1 for all entries)
        System.out.println("1. Weigher.singletonWeigher() - All entries have weight 1");
        Cache<String, String> cache1 = CacheBuilder.newBuilder()
                .maximumWeight(3)
                .weigher(Weigher.singletonWeigher())
                .build();
        System.out.println("   Equivalent to maximumSize(3)\n");

        // 2. Byte array weigher (weight = array length)
        System.out.println("2. Weigher.byteArrayWeigher() - Weight by byte array length");
        Cache<Integer, byte[]> cache2 = CacheBuilder.newBuilder()
                .maximumWeight(5000)
                .weigher(Weigher.byteArrayWeigher())
                .build();
        System.out.println("   Perfect for image/file caches\n");

        // 3. String weigher (weight = key.length + value.length)
        System.out.println("3. Weigher.stringWeigher() - Weight by total string length");
        Cache<String, String> cache3 = CacheBuilder.newBuilder()
                .maximumWeight(1000)
                .weigher(Weigher.stringWeigher())
                .build();
        System.out.println("   Good for text caches\n");

        // 4. Value string weigher (weight = value.length only)
        System.out.println("4. Weigher.valueStringWeigher() - Weight by value length only");
        Cache<Integer, String> cache4 = CacheBuilder.newBuilder()
                .maximumWeight(1000)
                .weigher(Weigher.valueStringWeigher())
                .build();
        System.out.println("   When keys are small/fixed size\n");

        // 5. Custom weigher
        System.out.println("5. Custom weigher - Application-specific logic");
        Cache<String, Document> cache5 = CacheBuilder.<String, Document>newBuilder()
                .maximumWeight(1_000_000)
                .weigher((Weigher<String, Document>) (key, doc) -> doc.estimatedSize())
                .build();
        System.out.println("   For complex objects with size estimation");
    }

    /**
     * Example document class for custom weigher demo.
     */
    private static class Document {
        private final String content;
        private final byte[] data;

        public Document(String content, byte[] data) {
            this.content = content;
            this.data = data;
        }

        /**
         * Estimates the memory footprint of this document.
         */
        public int estimatedSize() {
            // Rough estimate: string chars + byte array length + object overhead
            return (content != null ? content.length() * 2 : 0) +
                   (data != null ? data.length : 0) +
                   100; // Object overhead estimate
        }
    }
}
