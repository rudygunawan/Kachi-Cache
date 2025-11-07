package com.github.rudygunawan.kachi.policy;

import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

/**
 * Represents a time window with a specific refresh interval.
 * Used by {@link TimeBasedRefreshPolicy} to define periods where cache refresh rates differ.
 *
 * <p>A time window has:
 * <ul>
 *   <li>Start time (hour and minute)</li>
 *   <li>End time (hour and minute)</li>
 *   <li>Refresh interval (duration and time unit)</li>
 * </ul>
 *
 * <p>Time windows can span midnight (e.g., 22:00 to 02:00).
 *
 * <p>Example usage:
 * <pre>{@code
 * TimeWindow marketHours = TimeWindow.builder()
 *     .startTime(9, 30)
 *     .endTime(16, 0)
 *     .refreshEvery(1, TimeUnit.MINUTES)
 *     .build();
 *
 * TimeWindow lunchBreak = TimeWindow.builder()
 *     .startTime(12, 0)
 *     .endTime(13, 0)
 *     .refreshEvery(5, TimeUnit.MINUTES)
 *     .build();
 * }</pre>
 */
public class TimeWindow {
    private final int startHour;
    private final int startMinute;
    private final int endHour;
    private final int endMinute;
    private final long intervalNanos;
    private final LocalTime start;
    private final LocalTime end;

    private TimeWindow(int startHour, int startMinute, int endHour, int endMinute,
                      long interval, TimeUnit unit) {
        validateTime(startHour, startMinute, "start");
        validateTime(endHour, endMinute, "end");

        if (interval <= 0) {
            throw new IllegalArgumentException("Refresh interval must be positive, got: " + interval);
        }

        this.startHour = startHour;
        this.startMinute = startMinute;
        this.endHour = endHour;
        this.endMinute = endMinute;
        this.intervalNanos = unit.toNanos(interval);
        this.start = LocalTime.of(startHour, startMinute);
        this.end = LocalTime.of(endHour, endMinute);
    }

    private void validateTime(int hour, int minute, String label) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException(
                label + " hour must be between 0 and 23, got: " + hour);
        }
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException(
                label + " minute must be between 0 and 59, got: " + minute);
        }
    }

    /**
     * Creates a new builder for constructing a TimeWindow.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating TimeWindow instances with a fluent API.
     */
    public static class Builder {
        private Integer startHour;
        private Integer startMinute;
        private Integer endHour;
        private Integer endMinute;
        private Long interval;
        private TimeUnit unit;

        private Builder() {}

        /**
         * Sets the start time.
         * @param hour the start hour (0-23)
         * @param minute the start minute (0-59)
         */
        public Builder startTime(int hour, int minute) {
            this.startHour = hour;
            this.startMinute = minute;
            return this;
        }

        /**
         * Sets the start time (convenience method with minute = 0).
         * @param hour the start hour (0-23)
         */
        public Builder startTime(int hour) {
            return startTime(hour, 0);
        }

        /**
         * Sets the end time.
         * @param hour the end hour (0-23)
         * @param minute the end minute (0-59)
         */
        public Builder endTime(int hour, int minute) {
            this.endHour = hour;
            this.endMinute = minute;
            return this;
        }

        /**
         * Sets the end time (convenience method with minute = 0).
         * @param hour the end hour (0-23)
         */
        public Builder endTime(int hour) {
            return endTime(hour, 0);
        }

        /**
         * Sets the refresh interval.
         * @param interval the refresh interval duration
         * @param unit the time unit
         */
        public Builder refreshEvery(long interval, TimeUnit unit) {
            this.interval = interval;
            this.unit = unit;
            return this;
        }

        /**
         * Builds the TimeWindow instance.
         * @throws IllegalArgumentException if any required field is missing
         */
        public TimeWindow build() {
            if (startHour == null || startMinute == null) {
                throw new IllegalArgumentException("Start time must be set");
            }
            if (endHour == null || endMinute == null) {
                throw new IllegalArgumentException("End time must be set");
            }
            if (interval == null || unit == null) {
                throw new IllegalArgumentException("Refresh interval must be set");
            }

            return new TimeWindow(startHour, startMinute, endHour, endMinute, interval, unit);
        }
    }

    /**
     * Checks if the given time falls within this window.
     * Handles windows that cross midnight (e.g., 22:00 to 02:00).
     */
    public boolean contains(LocalTime time) {
        // Handle windows that cross midnight
        if (end.isBefore(start)) {
            return !time.isBefore(start) || !time.isAfter(end);
        } else {
            return !time.isBefore(start) && !time.isAfter(end);
        }
    }

    /**
     * Checks if this window overlaps with another window.
     * Two windows overlap if there exists any time point that falls within both windows.
     */
    public boolean overlaps(TimeWindow other) {
        // Check if either window's start or end falls within the other window
        return this.contains(other.start) ||
               this.contains(other.end) ||
               other.contains(this.start) ||
               other.contains(this.end);
    }

    public int getStartHour() {
        return startHour;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public int getEndHour() {
        return endHour;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public long getIntervalNanos() {
        return intervalNanos;
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return String.format("%02d:%02d-%02d:%02d (refresh every %d ns)",
                startHour, startMinute, endHour, endMinute, intervalNanos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeWindow that = (TimeWindow) o;
        return startHour == that.startHour &&
               startMinute == that.startMinute &&
               endHour == that.endHour &&
               endMinute == that.endMinute;
    }

    @Override
    public int hashCode() {
        int result = startHour;
        result = 31 * result + startMinute;
        result = 31 * result + endHour;
        result = 31 * result + endMinute;
        return result;
    }
}
