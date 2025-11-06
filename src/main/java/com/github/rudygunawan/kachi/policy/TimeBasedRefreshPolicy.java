package com.github.rudygunawan.kachi.policy;

import com.github.rudygunawan.kachi.api.RefreshPolicy;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A refresh policy that uses different refresh intervals during different time periods.
 * This is particularly useful for data that has predictable activity patterns, such as:
 * <ul>
 *   <li>Stock market data (active during trading hours, quiet after hours)
 *   <li>Business applications (frequent refresh during work hours, rare refresh at night)
 *   <li>Regional content (different refresh rates based on local time zones)
 * </ul>
 *
 * <p>Usage example for stock market data:
 * <pre>{@code
 * TimeBasedRefreshPolicy<String, StockPrice> policy =
 *     new TimeBasedRefreshPolicy<>(ZoneId.of("America/New_York"))
 *         .addActiveWindow(9, 30, 16, 0, 1, TimeUnit.MINUTES)  // 9:30am-4pm: refresh every minute
 *         .setDefaultInterval(10, TimeUnit.MINUTES);            // After hours: refresh every 10 min
 *
 * LoadingCache<String, StockPrice> cache = CacheBuilder.newBuilder()
 *     .refreshAfter(policy)
 *     .build(ticker -> loadStockPrice(ticker));
 * }</pre>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public class TimeBasedRefreshPolicy<K, V> implements RefreshPolicy<K, V> {

    /**
     * Represents a time window with a specific refresh interval.
     */
    public static class TimeWindow {
        private final int startHour;
        private final int startMinute;
        private final int endHour;
        private final int endMinute;
        private final long intervalNanos;

        public TimeWindow(int startHour, int startMinute, int endHour, int endMinute,
                         long interval, TimeUnit unit) {
            if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23) {
                throw new IllegalArgumentException("Hours must be between 0 and 23");
            }
            if (startMinute < 0 || startMinute > 59 || endMinute < 0 || endMinute > 59) {
                throw new IllegalArgumentException("Minutes must be between 0 and 59");
            }
            if (interval <= 0) {
                throw new IllegalArgumentException("Interval must be positive");
            }

            this.startHour = startHour;
            this.startMinute = startMinute;
            this.endHour = endHour;
            this.endMinute = endMinute;
            this.intervalNanos = unit.toNanos(interval);
        }

        /**
         * Checks if the given time falls within this window.
         */
        public boolean contains(LocalTime time) {
            LocalTime start = LocalTime.of(startHour, startMinute);
            LocalTime end = LocalTime.of(endHour, endMinute);

            // Handle windows that cross midnight
            if (end.isBefore(start)) {
                return !time.isBefore(start) || !time.isAfter(end);
            } else {
                return !time.isBefore(start) && !time.isAfter(end);
            }
        }

        public long getIntervalNanos() {
            return intervalNanos;
        }

        @Override
        public String toString() {
            return String.format("%02d:%02d-%02d:%02d (%d ns)",
                    startHour, startMinute, endHour, endMinute, intervalNanos);
        }
    }

    private final ZoneId timeZone;
    private final List<TimeWindow> windows;
    private long defaultIntervalNanos;

    /**
     * Creates a new time-based refresh policy using the system default time zone.
     */
    public TimeBasedRefreshPolicy() {
        this(ZoneId.systemDefault());
    }

    /**
     * Creates a new time-based refresh policy using the specified time zone.
     *
     * @param timeZone the time zone to use for time-based decisions
     */
    public TimeBasedRefreshPolicy(ZoneId timeZone) {
        this.timeZone = timeZone;
        this.windows = new ArrayList<>();
        this.defaultIntervalNanos = Long.MAX_VALUE; // No refresh by default
    }

    /**
     * Adds a time window during which entries should be refreshed at the specified interval.
     * Time windows are checked in the order they are added. The first matching window is used.
     *
     * @param startHour the start hour (0-23)
     * @param startMinute the start minute (0-59)
     * @param endHour the end hour (0-23)
     * @param endMinute the end minute (0-59)
     * @param interval the refresh interval
     * @param unit the time unit for the interval
     * @return this policy instance for chaining
     */
    public TimeBasedRefreshPolicy<K, V> addActiveWindow(int startHour, int startMinute,
                                                         int endHour, int endMinute,
                                                         long interval, TimeUnit unit) {
        windows.add(new TimeWindow(startHour, startMinute, endHour, endMinute, interval, unit));
        return this;
    }

    /**
     * Convenience method to add a time window using only hours (minutes = 0).
     *
     * @param startHour the start hour (0-23)
     * @param endHour the end hour (0-23)
     * @param interval the refresh interval
     * @param unit the time unit for the interval
     * @return this policy instance for chaining
     */
    public TimeBasedRefreshPolicy<K, V> addActiveWindow(int startHour, int endHour,
                                                         long interval, TimeUnit unit) {
        return addActiveWindow(startHour, 0, endHour, 0, interval, unit);
    }

    /**
     * Sets the default refresh interval to use when the current time doesn't fall within
     * any defined active window.
     *
     * @param interval the default refresh interval
     * @param unit the time unit for the interval
     * @return this policy instance for chaining
     */
    public TimeBasedRefreshPolicy<K, V> setDefaultInterval(long interval, TimeUnit unit) {
        if (interval <= 0) {
            throw new IllegalArgumentException("Interval must be positive");
        }
        this.defaultIntervalNanos = unit.toNanos(interval);
        return this;
    }

    /**
     * Disables automatic refresh outside of defined active windows.
     * This is the default behavior if no default interval is set.
     *
     * @return this policy instance for chaining
     */
    public TimeBasedRefreshPolicy<K, V> disableDefaultRefresh() {
        this.defaultIntervalNanos = Long.MAX_VALUE;
        return this;
    }

    @Override
    public long getRefreshInterval(K key, V value, long currentTime) {
        LocalTime now = ZonedDateTime.now(timeZone).toLocalTime();

        // Check each window in order
        for (TimeWindow window : windows) {
            if (window.contains(now)) {
                return window.getIntervalNanos();
            }
        }

        // No matching window, use default
        return defaultIntervalNanos;
    }

    @Override
    public void onRefreshSuccess(K key, V oldValue, V newValue, long currentTime) {
        // Could add logging or metrics here
    }

    @Override
    public void onRefreshFailure(K key, V value, Throwable error, long currentTime) {
        // Could add logging or metrics here
        // Default: keep old value and try again at next interval
    }

    /**
     * Returns the time zone used by this policy.
     */
    public ZoneId getTimeZone() {
        return timeZone;
    }

    /**
     * Returns the number of active windows configured.
     */
    public int getWindowCount() {
        return windows.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TimeBasedRefreshPolicy{timeZone=").append(timeZone);
        sb.append(", windows=[");
        for (int i = 0; i < windows.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(windows.get(i));
        }
        sb.append("], default=");
        if (defaultIntervalNanos == Long.MAX_VALUE) {
            sb.append("disabled");
        } else {
            sb.append(defaultIntervalNanos).append(" ns");
        }
        sb.append("}");
        return sb.toString();
    }
}
