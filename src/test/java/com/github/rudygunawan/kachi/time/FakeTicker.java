package com.github.rudygunawan.kachi.time;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A fake {@link Ticker} implementation for testing time-based cache features.
 *
 * <p>This ticker allows you to manually control time progression in tests, making it possible
 * to test expiration and refresh behavior without waiting for wall-clock time to pass.
 *
 * <p><b>Example usage:</b>
 * <pre>{@code
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
 * cache.cleanUp(); // Trigger cleanup
 * assertNull(cache.getIfPresent("user1"));
 * }</pre>
 *
 * <p>This class is thread-safe and can be used in concurrent tests.
 *
 * @since 0.3.0
 */
public class FakeTicker implements Ticker {

    private final AtomicLong nanos;

    /**
     * Creates a new fake ticker starting at time zero.
     */
    public FakeTicker() {
        this.nanos = new AtomicLong(0);
    }

    /**
     * Advances the ticker by the specified duration.
     *
     * <p>Time can only advance forward. Negative durations are treated as zero.
     *
     * @param duration the amount of time to advance
     * @param unit the time unit of the duration
     * @return this ticker, for method chaining
     */
    public FakeTicker advance(long duration, TimeUnit unit) {
        return advance(unit.toNanos(duration));
    }

    /**
     * Advances the ticker by the specified number of nanoseconds.
     *
     * <p>Time can only advance forward. Negative values are treated as zero.
     *
     * @param nanoseconds the number of nanoseconds to advance
     * @return this ticker, for method chaining
     */
    public FakeTicker advance(long nanoseconds) {
        if (nanoseconds > 0) {
            nanos.addAndGet(nanoseconds);
        }
        return this;
    }

    /**
     * Sets the ticker to a specific time value.
     *
     * <p><b>Warning:</b> This method allows setting time backwards, which may cause unexpected
     * behavior in caches. Use {@link #advance(long, TimeUnit)} instead for normal testing.
     *
     * @param nanoseconds the time value to set
     * @return this ticker, for method chaining
     */
    public FakeTicker setNanos(long nanoseconds) {
        nanos.set(nanoseconds);
        return this;
    }

    @Override
    public long read() {
        return nanos.get();
    }

    /**
     * Returns the current time value of this ticker in nanoseconds.
     *
     * @return the current time in nanoseconds
     */
    public long getNanos() {
        return nanos.get();
    }

    @Override
    public String toString() {
        return "FakeTicker(" + nanos.get() + " ns)";
    }
}
