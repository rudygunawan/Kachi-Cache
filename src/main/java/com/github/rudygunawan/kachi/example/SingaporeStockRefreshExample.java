package com.github.rudygunawan.kachi.example;

import com.github.rudygunawan.kachi.api.CacheLoader;
import com.github.rudygunawan.kachi.api.LoadingCache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.policy.TimeBasedRefreshPolicy;
import com.github.rudygunawan.kachi.policy.TimeWindow;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating time-based refresh for Singapore Stock Exchange (SGX).
 *
 * <p>Singapore Stock Exchange trading hours:
 * <ul>
 *   <li>Morning Session: 9:00am - 12:00pm SGT (refresh every 10 seconds)</li>
 *   <li>Lunch Break: 12:00pm - 1:00pm SGT (no trading, refresh disabled)</li>
 *   <li>Afternoon Session: 1:00pm - 5:00pm SGT (refresh every 10 seconds)</li>
 *   <li>After Hours: 5:00pm - 9:00am SGT (refresh every 30 seconds)</li>
 * </ul>
 *
 * <p>This example demonstrates:
 * <ul>
 *   <li>Multiple time windows in a single day</li>
 *   <li>Proper validation that time windows don't overlap</li>
 *   <li>Using the new TimeWindow builder API</li>
 *   <li>Different refresh rates for different trading periods</li>
 * </ul>
 */
public class SingaporeStockRefreshExample {

    /**
     * Simple stock price data model for SGX stocks.
     */
    static class StockPrice {
        private final String ticker;
        private final double price;
        private final LocalDateTime timestamp;

        public StockPrice(String ticker, double price) {
            this.ticker = ticker;
            this.price = price;
            this.timestamp = LocalDateTime.now(ZoneId.of("Asia/Singapore"));
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
            return String.format("%s: $%.2f SGD (loaded at %s SGT)",
                    ticker, price, timestamp.format(formatter));
        }
    }

    /**
     * Simulates loading stock price from SGX market data.
     */
    static class SGXStockPriceLoader extends CacheLoader<String, StockPrice> {
        private final Random random = new Random();
        private int loadCount = 0;

        @Override
        public StockPrice load(String ticker) throws Exception {
            loadCount++;
            // Simulate network delay
            Thread.sleep(50);

            // Simulate price fluctuation
            double basePrice = getBasePrice(ticker);
            double fluctuation = (random.nextDouble() - 0.5) * 2; // +/- $1 SGD
            double price = basePrice + fluctuation;

            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Singapore"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            System.out.println(String.format("[%s SGT] [LOAD #%d] Fetching %s price from SGX API...",
                    now.format(formatter), loadCount, ticker));

            return new StockPrice(ticker, price);
        }

        private double getBasePrice(String ticker) {
            // SGX blue chip stocks
            switch (ticker) {
                case "D05.SI": return 30.0;  // DBS Group Holdings
                case "O39.SI": return 12.0;  // OCBC Bank
                case "U11.SI": return 28.0;  // United Overseas Bank
                case "Z74.SI": return 3.5;   // Singapore Exchange
                case "C31.SI": return 4.2;   // CapitaLand Integrated Commercial Trust
                default: return 10.0;
            }
        }

        public int getLoadCount() {
            return loadCount;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Singapore Stock Exchange (SGX) Refresh Example ===\n");
        System.out.println("Trading Hours (Asia/Singapore timezone):");
        System.out.println("  Morning Session: 9:00am - 12:00pm (refresh every 10 seconds)");
        System.out.println("  Lunch Break:     12:00pm - 1:00pm (no trading)");
        System.out.println("  Afternoon Session: 1:00pm - 5:00pm (refresh every 10 seconds)");
        System.out.println("  After Hours:     5:00pm - 9:00am (refresh every 30 seconds)");
        System.out.println();

        // Define time windows using the new builder API
        TimeWindow morningSession = TimeWindow.builder()
                .startTime(9, 0)
                .endTime(12, 0)
                .refreshEvery(10, TimeUnit.SECONDS)
                .build();

        TimeWindow afternoonSession = TimeWindow.builder()
                .startTime(13, 0)  // 1:00pm
                .endTime(17, 0)    // 5:00pm
                .refreshEvery(10, TimeUnit.SECONDS)
                .build();

        List<TimeWindow> tradingHours = Arrays.asList(morningSession, afternoonSession);

        // Create time-based refresh policy with the new constructor
        TimeBasedRefreshPolicy<String, StockPrice> refreshPolicy =
                new TimeBasedRefreshPolicy<>(
                        ZoneId.of("Asia/Singapore"),
                        tradingHours,
                        30, TimeUnit.SECONDS  // Default interval for after hours
                );

        System.out.println("Refresh policy configured:");
        System.out.println("  " + refreshPolicy);
        System.out.println();

        // Create cache loader
        SGXStockPriceLoader loader = new SGXStockPriceLoader();

        // Create loading cache with time-based refresh
        LoadingCache<String, StockPrice> cache = CacheBuilder.newBuilder()
                .refreshAfter(refreshPolicy)
                .recordStats()
                .build(loader);

        // Simulate cache usage
        System.out.println("=== Starting Cache Simulation ===\n");

        String[] tickers = {"D05.SI", "O39.SI", "U11.SI", "Z74.SI", "C31.SI"};

        // Initial load of all stocks
        System.out.println("--- Initial Load ---");
        for (String ticker : tickers) {
            StockPrice price = cache.get(ticker);
            System.out.println("  " + price);
        }

        // Wait and access again - should hit cache
        System.out.println("\n--- Access After 5 Seconds (Should Hit Cache) ---");
        Thread.sleep(5000);
        for (String ticker : tickers) {
            StockPrice price = cache.getIfPresent(ticker);
            System.out.println("  " + price);
        }

        // Wait for refresh to occur (depending on current time)
        System.out.println("\n--- Waiting for Automatic Refresh ---");
        System.out.println("  (Refresh interval depends on current SGT time)");
        System.out.println("  (During trading hours: 10s, After hours: 30s)");
        Thread.sleep(15000);

        // Access again - may have refreshed values
        System.out.println("\n--- Access After Refresh Period ---");
        for (String ticker : tickers) {
            StockPrice price = cache.getIfPresent(ticker);
            if (price != null) {
                System.out.println("  " + price);
            }
        }

        // Display statistics
        System.out.println("\n=== Cache Statistics ===");
        System.out.println("  Total loads from SGX API: " + loader.getLoadCount());
        System.out.println("  Cache hits: " + cache.stats().hitCount());
        System.out.println("  Cache misses: " + cache.stats().missCount());
        System.out.println("  Hit rate: " + String.format("%.2f%%", cache.stats().hitRate() * 100));

        // Demonstrate overlap validation
        System.out.println("\n=== Demonstrating Overlap Validation ===");
        System.out.println("Attempting to create overlapping windows...");
        try {
            TimeWindow overlappingWindow = TimeWindow.builder()
                    .startTime(11, 0)
                    .endTime(13, 0)  // Overlaps with both morning and afternoon sessions
                    .refreshEvery(5, TimeUnit.SECONDS)
                    .build();

            List<TimeWindow> invalidWindows = Arrays.asList(
                    morningSession,
                    overlappingWindow,
                    afternoonSession
            );

            new TimeBasedRefreshPolicy<>(
                    ZoneId.of("Asia/Singapore"),
                    invalidWindows,
                    30, TimeUnit.SECONDS
            );

            System.out.println("  ✗ Validation failed - overlaps should have been detected!");
        } catch (IllegalArgumentException e) {
            System.out.println("  ✓ Overlap detected successfully!");
            System.out.println("  Error: " + e.getMessage());
        }

        System.out.println("\n=== Example Complete ===");
    }
}
