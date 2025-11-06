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
 * <p><b>New API (recommended):</b> Use builder pattern for cleaner code:
 * <pre>{@code
 * // Singapore Stock Exchange: 9-12, lunch break, 13-16
 * TimeWindow morningSession = TimeWindow.builder()
 *     .startTime(9, 0)
 *     .endTime(12, 0)
 *     .refreshEvery(10, TimeUnit.SECONDS)
 *     .build();
 *
 * TimeWindow afternoonSession = TimeWindow.builder()
 *     .startTime(13, 0)
 *     .endTime(16, 0)
 *     .refreshEvery(10, TimeUnit.SECONDS)
 *     .build();
 *
 * List<TimeWindow> activeWindows = Arrays.asList(morningSession, afternoonSession);
 *
 * TimeBasedRefreshPolicy<String, StockPrice> policy =
 *     new TimeBasedRefreshPolicy<>(ZoneId.of("Asia/Singapore"), activeWindows, 30, TimeUnit.SECONDS);
 *
 * LoadingCache<String, StockPrice> cache = CacheBuilder.newBuilder()
 *     .refreshAfter(policy)
 *     .build(ticker -> loadStockPrice(ticker));
 * }</pre>
 *
 * <p><b>Legacy API:</b> Fluent API still supported:
 * <pre>{@code
 * TimeBasedRefreshPolicy<String, StockPrice> policy =
 *     new TimeBasedRefreshPolicy<>(ZoneId.of("America/New_York"))
 *         .addActiveWindow(9, 30, 16, 0, 1, TimeUnit.MINUTES)
 *         .setDefaultInterval(10, TimeUnit.MINUTES);
 * }</pre>
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public class TimeBasedRefreshPolicy<K, V> implements RefreshPolicy<K, V> {

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
     * Use this constructor with the fluent API (addActiveWindow, setDefaultInterval).
     *
     * @param timeZone the time zone to use for time-based decisions
     */
    public TimeBasedRefreshPolicy(ZoneId timeZone) {
        this.timeZone = timeZone;
        this.windows = new ArrayList<>();
        this.defaultIntervalNanos = Long.MAX_VALUE; // No refresh by default
    }

    /**
     * Creates a new time-based refresh policy with a list of active windows.
     * This is the recommended constructor for the new API.
     *
     * <p>Example (Singapore Stock Exchange):
     * <pre>{@code
     * TimeWindow morning = TimeWindow.builder()
     *     .startTime(9, 0).endTime(12, 0).refreshEvery(10, TimeUnit.SECONDS).build();
     * TimeWindow afternoon = TimeWindow.builder()
     *     .startTime(13, 0).endTime(16, 0).refreshEvery(10, TimeUnit.SECONDS).build();
     *
     * TimeBasedRefreshPolicy<String, StockPrice> policy =
     *     new TimeBasedRefreshPolicy<>(
     *         ZoneId.of("Asia/Singapore"),
     *         Arrays.asList(morning, afternoon),
     *         30, TimeUnit.SECONDS  // default interval (after hours)
     *     );
     * }</pre>
     *
     * @param timeZone the time zone to use for time-based decisions
     * @param windows the list of active time windows (must not be null or empty)
     * @param defaultInterval the default refresh interval outside active windows
     * @param defaultUnit the time unit for the default interval
     * @throws IllegalArgumentException if windows is null, empty, or contains overlapping windows
     */
    public TimeBasedRefreshPolicy(ZoneId timeZone, List<TimeWindow> windows,
                                   long defaultInterval, TimeUnit defaultUnit) {
        if (windows == null || windows.isEmpty()) {
            throw new IllegalArgumentException("Active windows list must not be null or empty");
        }
        if (defaultInterval <= 0) {
            throw new IllegalArgumentException("Default interval must be positive, got: " + defaultInterval);
        }

        this.timeZone = timeZone;
        this.windows = new ArrayList<>(windows); // Make defensive copy
        this.defaultIntervalNanos = defaultUnit.toNanos(defaultInterval);

        // Validate that no windows overlap
        validateNoOverlaps();
    }

    /**
     * Creates a new time-based refresh policy with a list of active windows (no default refresh).
     * Outside the defined windows, refresh is disabled.
     *
     * @param timeZone the time zone to use for time-based decisions
     * @param windows the list of active time windows (must not be null or empty)
     * @throws IllegalArgumentException if windows is null, empty, or contains overlapping windows
     */
    public TimeBasedRefreshPolicy(ZoneId timeZone, List<TimeWindow> windows) {
        if (windows == null || windows.isEmpty()) {
            throw new IllegalArgumentException("Active windows list must not be null or empty");
        }

        this.timeZone = timeZone;
        this.windows = new ArrayList<>(windows); // Make defensive copy
        this.defaultIntervalNanos = Long.MAX_VALUE; // No refresh by default

        // Validate that no windows overlap
        validateNoOverlaps();
    }

    /**
     * Validates that no time windows overlap with each other.
     * @throws IllegalArgumentException if any two windows overlap
     */
    private void validateNoOverlaps() {
        for (int i = 0; i < windows.size(); i++) {
            for (int j = i + 1; j < windows.size(); j++) {
                TimeWindow window1 = windows.get(i);
                TimeWindow window2 = windows.get(j);
                if (window1.overlaps(window2)) {
                    throw new IllegalArgumentException(
                        String.format("Time windows overlap: %s and %s",
                            window1, window2));
                }
            }
        }
    }

    /**
     * Adds a time window during which entries should be refreshed at the specified interval.
     * Time windows are checked in the order they are added. The first matching window is used.
     *
     * <p><b>Note:</b> This method validates that the new window doesn't overlap with existing windows.
     *
     * @param startHour the start hour (0-23)
     * @param startMinute the start minute (0-59)
     * @param endHour the end hour (0-23)
     * @param endMinute the end minute (0-59)
     * @param interval the refresh interval
     * @param unit the time unit for the interval
     * @return this policy instance for chaining
     * @throws IllegalArgumentException if the new window overlaps with any existing window
     * @deprecated Use the constructor with List<TimeWindow> for better validation
     */
    @Deprecated
    public TimeBasedRefreshPolicy<K, V> addActiveWindow(int startHour, int startMinute,
                                                         int endHour, int endMinute,
                                                         long interval, TimeUnit unit) {
        TimeWindow newWindow = TimeWindow.builder()
            .startTime(startHour, startMinute)
            .endTime(endHour, endMinute)
            .refreshEvery(interval, unit)
            .build();

        // Check for overlaps with existing windows
        for (TimeWindow existing : windows) {
            if (existing.overlaps(newWindow)) {
                throw new IllegalArgumentException(
                    String.format("New window %s overlaps with existing window %s",
                        newWindow, existing));
            }
        }

        windows.add(newWindow);
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
