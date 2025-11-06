package com.github.rudygunawan.kachi.example;

import com.github.rudygunawan.kachi.api.CacheLoader;
import com.github.rudygunawan.kachi.api.LoadingCache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.policy.TimeBasedRefreshPolicy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating time-based refresh for stock market data.
 * Stock prices are refreshed more frequently during market hours (9:30am-4pm EST)
 * and less frequently after hours.
 *
 * This example simulates:
 * - Market hours (9:30am-4pm EST): Refresh every 1 minute
 * - After hours: Refresh every 10 minutes
 */
public class StockMarketRefreshExample {

    /**
     * Simple stock price data model.
     */
    static class StockPrice {
        private final String ticker;
        private final double price;
        private final LocalDateTime timestamp;

        public StockPrice(String ticker, double price) {
            this.ticker = ticker;
            this.price = price;
            this.timestamp = LocalDateTime.now();
        }

        public String getTicker() {
            return ticker;
        }

        public double getPrice() {
            return price;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            return String.format("%s: $%.2f (loaded at %s)",
                    ticker, price, timestamp.format(formatter));
        }
    }

    /**
     * Simulates loading stock price from an external source.
     */
    static class StockPriceLoader extends CacheLoader<String, StockPrice> {
        private final Random random = new Random();
        private int loadCount = 0;

        @Override
        public StockPrice load(String ticker) throws Exception {
            loadCount++;
            // Simulate network delay
            Thread.sleep(100);

            // Simulate price fluctuation
            double basePrice = getBasePrice(ticker);
            double fluctuation = (random.nextDouble() - 0.5) * 10; // +/- $5
            double price = basePrice + fluctuation;

            System.out.println(String.format("[LOAD #%d] Fetching %s price from market data API...",
                    loadCount, ticker));

            return new StockPrice(ticker, price);
        }

        private double getBasePrice(String ticker) {
            switch (ticker) {
                case "AAPL": return 150.0;
                case "GOOGL": return 2800.0;
                case "MSFT": return 300.0;
                case "TSLA": return 700.0;
                default: return 100.0;
            }
        }

        public int getLoadCount() {
            return loadCount;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Stock Market Refresh Example ===\n");
        System.out.println("This example demonstrates time-based refresh where cache entries");
        System.out.println("are refreshed at different rates during different time periods.\n");

        // Example 1: Time-based refresh policy for NASDAQ stocks
        example1_MarketHoursRefresh();

        // Example 2: Simple fixed refresh interval
        example2_FixedRefreshInterval();

        System.out.println("\n=== Example Complete ===");
    }

    /**
     * Example 1: Stock cache with different refresh rates during market hours vs after hours.
     */
    private static void example1_MarketHoursRefresh() throws Exception {
        System.out.println("Example 1: Time-Based Refresh (Market Hours)");
        System.out.println("---------------------------------------------");
        System.out.println("Market hours (9:30am-4pm EST): Refresh every 10 seconds (simulating 1 min)");
        System.out.println("After hours: Refresh every 30 seconds (simulating 10 min)");
        System.out.println();

        // Create time-based refresh policy for NASDAQ stocks
        TimeBasedRefreshPolicy<String, StockPrice> refreshPolicy =
                new TimeBasedRefreshPolicy<String, StockPrice>(ZoneId.of("America/New_York"));

        // Market hours: 9:30am - 4:00pm EST
        // For demo purposes, using 10 seconds instead of 1 minute
        refreshPolicy.addActiveWindow(9, 30, 16, 0, 10, TimeUnit.SECONDS);

        // After hours: refresh every 30 seconds (demo) instead of 10 min
        refreshPolicy.setDefaultInterval(30, TimeUnit.SECONDS);

        System.out.println("Refresh policy: " + refreshPolicy);
        System.out.println();

        StockPriceLoader loader = new StockPriceLoader();

        // Create loading cache with refresh policy
        LoadingCache<String, StockPrice> cache = CacheBuilder.newBuilder()
                .<String, StockPrice>refreshAfter(refreshPolicy)
                .recordStats()
                .build(loader);

        // Load initial stock prices
        String[] tickers = {"AAPL", "GOOGL", "MSFT", "TSLA"};

        System.out.println("Loading initial stock prices...");
        for (String ticker : tickers) {
            StockPrice price = cache.get(ticker);
            System.out.println("  " + price);
        }
        System.out.println();

        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/New_York"));
        boolean isMarketHours = isWithinMarketHours(now);

        System.out.println("Current time (EST): " + now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        System.out.println("Market status: " + (isMarketHours ? "OPEN" : "CLOSED"));
        System.out.println();

        if (isMarketHours) {
            System.out.println("Market is OPEN - prices will refresh every ~10 seconds");
        } else {
            System.out.println("Market is CLOSED - prices will refresh every ~30 seconds");
        }

        System.out.println("\nMonitoring cache for 90 seconds...");
        System.out.println("Watch for automatic refresh messages (background reloads):\n");

        // Monitor for 90 seconds
        long startTime = System.currentTimeMillis();
        int checkCount = 0;
        while (System.currentTimeMillis() - startTime < 90000) {
            Thread.sleep(15000); // Check every 15 seconds
            checkCount++;

            System.out.println(String.format("[Check #%d at %ds] Current prices:",
                    checkCount, (System.currentTimeMillis() - startTime) / 1000));

            for (String ticker : tickers) {
                StockPrice price = cache.getIfPresent(ticker);
                if (price != null) {
                    System.out.println("  " + price);
                }
            }
            System.out.println("  Total loads: " + loader.getLoadCount());
            System.out.println();
        }

        System.out.println("Total cache loads: " + loader.getLoadCount());
        System.out.println("Initial loads: 4, Refreshes: " + (loader.getLoadCount() - 4));
        System.out.println();

        // Shutdown
        if (cache instanceof com.github.rudygunawan.kachi.impl.ConcurrentCacheImpl) {
            ((com.github.rudygunawan.kachi.impl.ConcurrentCacheImpl<?, ?>) cache).shutdown();
        }
    }

    /**
     * Example 2: Simple fixed refresh interval (like Caffeine's refreshAfterWrite).
     */
    private static void example2_FixedRefreshInterval() throws Exception {
        System.out.println("Example 2: Fixed Refresh Interval");
        System.out.println("----------------------------------");
        System.out.println("All entries refresh every 20 seconds\n");

        StockPriceLoader loader = new StockPriceLoader();

        // Create loading cache with fixed refresh interval
        LoadingCache<String, StockPrice> cache = CacheBuilder.newBuilder()
                .<String, StockPrice>refreshAfterWrite(20, TimeUnit.SECONDS)
                .build(loader);

        // Load initial prices
        System.out.println("Loading initial prices...");
        StockPrice aapl = cache.get("AAPL");
        StockPrice googl = cache.get("GOOGL");
        System.out.println("  " + aapl);
        System.out.println("  " + googl);
        System.out.println();

        System.out.println("Monitoring for 65 seconds...\n");

        // Monitor for 65 seconds
        long startTime = System.currentTimeMillis();
        int checkCount = 0;
        while (System.currentTimeMillis() - startTime < 65000) {
            Thread.sleep(15000); // Check every 15 seconds
            checkCount++;

            System.out.println(String.format("[Check #%d at %ds]:",
                    checkCount, (System.currentTimeMillis() - startTime) / 1000));

            aapl = cache.getIfPresent("AAPL");
            googl = cache.getIfPresent("GOOGL");
            System.out.println("  " + aapl);
            System.out.println("  " + googl);
            System.out.println("  Total loads: " + loader.getLoadCount());
            System.out.println();
        }

        System.out.println("Total cache loads: " + loader.getLoadCount());
        System.out.println();

        // Shutdown
        if (cache instanceof com.github.rudygunawan.kachi.impl.ConcurrentCacheImpl) {
            ((com.github.rudygunawan.kachi.impl.ConcurrentCacheImpl<?, ?>) cache).shutdown();
        }
    }

    /**
     * Helper method to check if current time is within market hours.
     */
    private static boolean isWithinMarketHours(LocalDateTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();

        // Market hours: 9:30am - 4:00pm
        if (hour < 9 || hour >= 16) {
            return false;
        }
        if (hour == 9 && minute < 30) {
            return false;
        }
        return true;
    }
}
