package com.github.rudygunawan.kachi.example;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.Expiry;
import com.github.rudygunawan.kachi.builder.CacheBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating per-entry expiration feature.
 * Different entries can have different TTLs based on their key, value, or application logic.
 */
public class PerEntryExpirationExample {

    /**
     * Simple User class to demonstrate variable TTL based on user properties.
     */
    static class User {
        private final String name;
        private final boolean premium;
        private final int priority; // 1 = low, 2 = medium, 3 = high

        public User(String name, boolean premium, int priority) {
            this.name = name;
            this.premium = premium;
            this.priority = priority;
        }

        public String getName() {
            return name;
        }

        public boolean isPremium() {
            return premium;
        }

        public int getPriority() {
            return priority;
        }

        @Override
        public String toString() {
            return "User{name='" + name + "', premium=" + premium + ", priority=" + priority + "}";
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Kachi Cache Per-Entry Expiration Example ===\n");

        // Example 1: Simple custom expiry based on value properties
        example1_PremiumUserCaching();

        // Example 2: Priority-based expiration
        example2_PriorityBasedExpiration();

        // Example 3: Key-based expiration (e.g., different TTL per data type)
        example3_KeyBasedExpiration();

        // Example 4: Combining custom expiry with fixed TTL
        example4_CombinedExpiry();

        System.out.println("\n=== Example Complete ===");
    }

    /**
     * Example 1: Cache premium users longer than regular users.
     */
    private static void example1_PremiumUserCaching() throws InterruptedException {
        System.out.println("Example 1: Premium vs Regular Users");
        System.out.println("------------------------------------");

        Expiry<String, User> userExpiry = new Expiry<String, User>() {
            @Override
            public long expireAfterCreate(String key, User user, long currentTime) {
                // Premium users get 2 hours cache time
                // Regular users get 30 minutes
                return user.isPremium()
                        ? TimeUnit.HOURS.toNanos(2)
                        : TimeUnit.MINUTES.toNanos(30);
            }

            @Override
            public long expireAfterUpdate(String key, User user, long currentTime, long currentDuration) {
                // Keep the same duration on update
                return currentDuration;
            }

            @Override
            public long expireAfterRead(String key, User user, long currentTime, long currentDuration) {
                // No change on read
                return currentDuration;
            }
        };

        Cache<String, User> cache = CacheBuilder.newBuilder()
                .<String, User>expireAfter(userExpiry)
                .recordStats()
                .build();

        // Add users
        cache.put("user1", new User("Alice Premium", true, 3));
        cache.put("user2", new User("Bob Regular", false, 1));

        System.out.println("Added users to cache:");
        System.out.println("  user1 (premium): " + cache.getIfPresent("user1"));
        System.out.println("  user2 (regular): " + cache.getIfPresent("user2"));
        System.out.println("Cache size: " + cache.size());
        System.out.println();
    }

    /**
     * Example 2: Priority-based expiration with multiple levels.
     */
    private static void example2_PriorityBasedExpiration() {
        System.out.println("Example 2: Priority-Based Expiration");
        System.out.println("-------------------------------------");

        Expiry<String, User> priorityExpiry = new Expiry<String, User>() {
            @Override
            public long expireAfterCreate(String key, User user, long currentTime) {
                // High priority: 1 hour
                // Medium priority: 30 minutes
                // Low priority: 10 minutes
                switch (user.getPriority()) {
                    case 3:
                        return TimeUnit.HOURS.toNanos(1);
                    case 2:
                        return TimeUnit.MINUTES.toNanos(30);
                    case 1:
                    default:
                        return TimeUnit.MINUTES.toNanos(10);
                }
            }

            @Override
            public long expireAfterUpdate(String key, User user, long currentTime, long currentDuration) {
                // Recalculate on update (user priority might have changed)
                return expireAfterCreate(key, user, currentTime);
            }

            @Override
            public long expireAfterRead(String key, User user, long currentTime, long currentDuration) {
                // Keep same duration
                return currentDuration;
            }
        };

        Cache<String, User> cache = CacheBuilder.newBuilder()
                .<String, User>expireAfter(priorityExpiry)
                .build();

        // Add users with different priorities
        cache.put("high", new User("High Priority User", false, 3));
        cache.put("medium", new User("Medium Priority User", false, 2));
        cache.put("low", new User("Low Priority User", false, 1));

        System.out.println("Added users with different priorities:");
        System.out.println("  High priority (1 hour TTL): " + cache.getIfPresent("high"));
        System.out.println("  Medium priority (30 min TTL): " + cache.getIfPresent("medium"));
        System.out.println("  Low priority (10 min TTL): " + cache.getIfPresent("low"));
        System.out.println("Cache size: " + cache.size());
        System.out.println();
    }

    /**
     * Example 3: Key-based expiration (e.g., different TTL per data type).
     */
    private static void example3_KeyBasedExpiration() {
        System.out.println("Example 3: Key-Based Expiration");
        System.out.println("--------------------------------");

        Expiry<String, String> keyExpiry = new Expiry<String, String>() {
            @Override
            public long expireAfterCreate(String key, String value, long currentTime) {
                // Different TTL based on key prefix
                if (key.startsWith("session:")) {
                    return TimeUnit.MINUTES.toNanos(30); // Session data: 30 minutes
                } else if (key.startsWith("config:")) {
                    return TimeUnit.HOURS.toNanos(24); // Config data: 24 hours
                } else if (key.startsWith("temp:")) {
                    return TimeUnit.MINUTES.toNanos(5); // Temp data: 5 minutes
                } else {
                    return TimeUnit.HOURS.toNanos(1); // Default: 1 hour
                }
            }

            @Override
            public long expireAfterUpdate(String key, String value, long currentTime, long currentDuration) {
                // Recalculate on update
                return expireAfterCreate(key, value, currentTime);
            }

            @Override
            public long expireAfterRead(String key, String value, long currentTime, long currentDuration) {
                // Keep same duration
                return currentDuration;
            }
        };

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .<String, String>expireAfter(keyExpiry)
                .build();

        // Add data with different key prefixes
        cache.put("session:user123", "session data");
        cache.put("config:app.name", "My App");
        cache.put("temp:processing", "temp data");
        cache.put("other:data", "some data");

        System.out.println("Added data with different key types:");
        System.out.println("  session:user123 (30 min): " + cache.getIfPresent("session:user123"));
        System.out.println("  config:app.name (24 hours): " + cache.getIfPresent("config:app.name"));
        System.out.println("  temp:processing (5 min): " + cache.getIfPresent("temp:processing"));
        System.out.println("  other:data (1 hour): " + cache.getIfPresent("other:data"));
        System.out.println("Cache size: " + cache.size());
        System.out.println();
    }

    /**
     * Example 4: Combining custom expiry with fixed TTL (both can be specified).
     */
    private static void example4_CombinedExpiry() {
        System.out.println("Example 4: Combined Custom and Fixed Expiry");
        System.out.println("--------------------------------------------");

        Expiry<String, User> customExpiry = new Expiry<String, User>() {
            @Override
            public long expireAfterCreate(String key, User user, long currentTime) {
                // Custom logic
                return user.isPremium()
                        ? TimeUnit.HOURS.toNanos(2)
                        : TimeUnit.MINUTES.toNanos(30);
            }

            @Override
            public long expireAfterUpdate(String key, User user, long currentTime, long currentDuration) {
                return currentDuration;
            }

            @Override
            public long expireAfterRead(String key, User user, long currentTime, long currentDuration) {
                return currentDuration;
            }
        };

        // Note: When both expireAfter() and expireAfterWrite() are set,
        // the entry expires when EITHER condition is met
        Cache<String, User> cache = CacheBuilder.newBuilder()
                .<String, User>expireAfter(customExpiry)
                .expireAfterWrite(1, TimeUnit.HOURS) // Additional fixed TTL
                .build();

        cache.put("user1", new User("Premium User", true, 3));

        System.out.println("Cache with both custom and fixed expiry:");
        System.out.println("  Custom expiry gives premium users 2 hours");
        System.out.println("  Fixed expiry adds a 1-hour maximum for all entries");
        System.out.println("  Result: Premium user entry will expire after 1 hour (min of both)");
        System.out.println("Cache size: " + cache.size());
        System.out.println();
    }
}
