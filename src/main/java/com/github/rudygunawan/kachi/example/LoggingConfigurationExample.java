package com.github.rudygunawan.kachi.example;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.Expiry;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.listener.RemovalListener;
import com.github.rudygunawan.kachi.policy.EvictionPolicy;
import com.github.rudygunawan.kachi.policy.RemovalCause;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

/**
 * Example demonstrating how to configure Kachi cache logging.
 *
 * <p>Kachi uses java.util.logging (JUL) for all internal logging, following the
 * same approach as Google Guava and Caffeine. This keeps the library
 * dependency-free while providing flexible logging configuration.
 */
public class LoggingConfigurationExample {

    public static void main(String[] args) throws IOException {
        System.out.println("=== Kachi Cache Logging Configuration Examples ===\n");

        // Example 1: Default configuration (WARNING level)
        example1_DefaultLogging();

        System.out.println("\n" + "=".repeat(80) + "\n");

        // Example 2: Enable DEBUG logging (FINE level)
        example2_DebugLogging();

        System.out.println("\n" + "=".repeat(80) + "\n");

        // Example 3: Silent mode (no logging)
        example3_SilentLogging();

        System.out.println("\n" + "=".repeat(80) + "\n");

        // Example 4: Custom handler and format
        example4_CustomLogging();

        System.out.println("\n=== Examples Complete ===");
    }

    /**
     * Example 1: Default logging (WARNING level).
     * Only errors in custom policies/listeners are logged.
     */
    private static void example1_DefaultLogging() {
        System.out.println("=== Example 1: Default Logging (WARNING level) ===");
        System.out.println("Only errors will be logged (custom policy failures, listener exceptions)\n");

        // No special configuration needed - WARNING is the default
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(10)
                .expireAfter(new Expiry<String, String>() {
                    @Override
                    public long expireAfterCreate(String key, String value, long currentTime) {
                        // Simulate an error in custom expiry policy
                        if (key.equals("bad-key")) {
                            throw new RuntimeException("Simulated expiry policy error");
                        }
                        return TimeUnit.MINUTES.toNanos(5);
                    }

                    @Override
                    public long expireAfterUpdate(String key, String value, long currentTime, long currentDuration) {
                        return expireAfterCreate(key, value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(String key, String value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();

        // This will trigger a WARNING log (expiry policy error)
        cache.put("bad-key", "value");
        System.out.println("Put 'bad-key' - should see WARNING log above about expiry policy error");

        // Normal operation - no logs
        cache.put("good-key", "value");
        System.out.println("Put 'good-key' - no logs (normal operation)");
    }

    /**
     * Example 2: Enable debug logging to see evictions and refreshes.
     */
    private static void example2_DebugLogging() {
        System.out.println("=== Example 2: Debug Logging (FINE level) ===");
        System.out.println("Will see eviction logs when cache size limit is exceeded\n");

        // Configure logger to FINE level
        Logger logger = Logger.getLogger("com.github.rudygunawan.kachi.Cache");
        logger.setLevel(Level.FINE);

        // Add console handler if not already present
        if (logger.getHandlers().length == 0) {
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(Level.FINE);
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);
        }

        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(5)
                .evictionPolicy(EvictionPolicy.LRU)
                .recordStats()
                .build();

        // Fill cache to capacity
        System.out.println("Adding 5 entries (cache capacity = 5)...");
        for (int i = 1; i <= 5; i++) {
            cache.put(i, "value" + i);
        }

        // Add more entries to trigger evictions
        System.out.println("\nAdding 3 more entries to trigger evictions...");
        for (int i = 6; i <= 8; i++) {
            cache.put(i, "value" + i);
            // You should see FINE logs about evictions above
        }

        System.out.println("\nFinal cache size: " + cache.size());
        System.out.println("Total evictions: " + cache.stats().evictionCount());

        // Reset logger level
        logger.setLevel(Level.WARNING);
    }

    /**
     * Example 3: Silent mode - disable all logging.
     */
    private static void example3_SilentLogging() {
        System.out.println("=== Example 3: Silent Mode (OFF level) ===");
        System.out.println("No logs will be produced, even for errors\n");

        // Disable all logging
        Logger logger = Logger.getLogger("com.github.rudygunawan.kachi.Cache");
        logger.setLevel(Level.OFF);

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(5)
                .removalListener(new RemovalListener<String, String>() {
                    @Override
                    public void onRemoval(String key, String value, RemovalCause cause) {
                        // Simulate listener error - normally would be logged
                        throw new RuntimeException("Simulated listener error");
                    }
                })
                .build();

        cache.put("key1", "value1");
        cache.invalidate("key1");  // Triggers listener error, but no log

        System.out.println("Triggered listener error - but no logs (silent mode)");

        // Reset logger level
        logger.setLevel(Level.WARNING);
    }

    /**
     * Example 4: Custom handler and format for production.
     */
    private static void example4_CustomLogging() throws IOException {
        System.out.println("=== Example 4: Custom Handler and Format ===");
        System.out.println("Using custom formatter for structured logging\n");

        Logger logger = Logger.getLogger("com.github.rudygunawan.kachi.Cache");

        // Remove default handlers
        for (Handler handler : logger.getHandlers()) {
            logger.removeHandler(handler);
        }

        // Add custom handler with custom formatter
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return String.format("[%s] [%s] %s: %s%s%n",
                        record.getLevel(),
                        Thread.currentThread().getName(),
                        record.getLoggerName(),
                        record.getMessage(),
                        record.getThrown() != null ? " - " + record.getThrown().getMessage() : "");
            }
        });
        logger.addHandler(handler);
        logger.setLevel(Level.FINE);
        logger.setUseParentHandlers(false);  // Don't propagate to parent

        // Create cache and trigger some logs
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(3)
                .evictionPolicy(EvictionPolicy.WINDOW_TINY_LFU)
                .build();

        System.out.println("Adding entries to trigger evictions with custom format...");
        for (int i = 1; i <= 5; i++) {
            cache.put("key" + i, "value" + i);
        }

        System.out.println("\nCustom log format shows: [LEVEL] [THREAD] LOGGER: MESSAGE");

        // Cleanup
        logger.removeHandler(handler);
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(true);
    }

    /**
     * Bonus: Programmatic configuration from file.
     */
    public static void configureFromFile(String configFile) throws IOException {
        System.setProperty("java.util.logging.config.file", configFile);
        LogManager.getLogManager().readConfiguration();
        System.out.println("Logging configured from: " + configFile);
    }

    /**
     * Bonus: Quick setup for common scenarios.
     */
    public static class QuickSetup {

        /**
         * Production mode: Only log errors (WARNING level).
         */
        public static void production() {
            Logger.getLogger("com.github.rudygunawan.kachi.Cache").setLevel(Level.WARNING);
        }

        /**
         * Debug mode: Log all operations (FINE level).
         */
        public static void debug() {
            Logger logger = Logger.getLogger("com.github.rudygunawan.kachi.Cache");
            logger.setLevel(Level.FINE);

            // Ensure console handler is configured
            if (logger.getHandlers().length == 0) {
                ConsoleHandler handler = new ConsoleHandler();
                handler.setLevel(Level.FINE);
                logger.addHandler(handler);
            }
        }

        /**
         * Silent mode: No logging at all (OFF level).
         */
        public static void silent() {
            Logger.getLogger("com.github.rudygunawan.kachi.Cache").setLevel(Level.OFF);
        }

        /**
         * Custom level: Set specific logging level.
         */
        public static void custom(Level level) {
            Logger logger = Logger.getLogger("com.github.rudygunawan.kachi.Cache");
            logger.setLevel(level);

            // Ensure console handler is configured
            if (logger.getHandlers().length == 0) {
                ConsoleHandler handler = new ConsoleHandler();
                handler.setLevel(Level.ALL);
                logger.addHandler(handler);
            }
        }
    }
}
