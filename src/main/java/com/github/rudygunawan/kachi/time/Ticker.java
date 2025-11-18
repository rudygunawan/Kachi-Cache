package com.github.rudygunawan.kachi.time;

/**
 * A time source that returns the current time in nanoseconds.
 *
 * <p>The primary purpose of this interface is to facilitate testing of time-based cache features
 * (expiration, refresh) without relying on the system clock. During testing, you can provide a
 * fake Ticker implementation that you control.
 *
 * <p>This is similar to Caffeine's Ticker interface and provides the same testing capabilities.
 *
 * <p><b>Production Usage:</b>
 * <pre>{@code
 * // Use system time (default)
 * Cache<String, User> cache = CacheBuilder.newBuilder()
 *     .expireAfterWrite(10, TimeUnit.MINUTES)
 *     .build();
 *
 * // Or explicitly specify system ticker
 * Cache<String, User> cache = CacheBuilder.newBuilder()
 *     .ticker(Ticker.systemTicker())
 *     .expireAfterWrite(10, TimeUnit.MINUTES)
 *     .build();
 * }</pre>
 *
 * <p><b>Testing Usage:</b>
 * <pre>{@code
 * // Create a fake ticker for testing
 * FakeTicker ticker = new FakeTicker();
 *
 * Cache<String, User> cache = CacheBuilder.newBuilder()
 *     .ticker(ticker)
 *     .expireAfterWrite(10, TimeUnit.MINUTES)
 *     .build();
 *
 * cache.put("user1", user);
 * assertNotNull(cache.getIfPresent("user1"));
 *
 * // Advance time by 11 minutes
 * ticker.advance(11, TimeUnit.MINUTES);
 *
 * // Entry should be expired
 * assertNull(cache.getIfPresent("user1"));
 * }</pre>
 *
 * @since 0.3.0
 */
@FunctionalInterface
public interface Ticker {

    /**
     * Returns the number of nanoseconds elapsed since some fixed but arbitrary point in time.
     *
     * <p><b>Note:</b> This method should have the same properties as {@link System#nanoTime()}:
     * <ul>
     *   <li>Returns values in nanoseconds</li>
     *   <li>Monotonically increasing (never goes backwards)</li>
     *   <li>Not related to wall-clock time</li>
     * </ul>
     *
     * @return the number of nanoseconds elapsed since some arbitrary point in time
     */
    long read();

    /**
     * Returns a ticker that reads the current time using {@link System#nanoTime()}.
     *
     * <p>This is the default ticker used by caches when no custom ticker is specified.
     *
     * @return a ticker that uses the system's nanosecond-precision clock
     */
    static Ticker systemTicker() {
        return SystemTicker.INSTANCE;
    }

    /**
     * Default system ticker implementation using System.nanoTime().
     */
    enum SystemTicker implements Ticker {
        INSTANCE;

        @Override
        public long read() {
            return System.nanoTime();
        }

        @Override
        public String toString() {
            return "Ticker.systemTicker()";
        }
    }
}
