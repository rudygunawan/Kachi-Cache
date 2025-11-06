package com.github.rudygunawan.kachi.metrics;

/**
 * Represents the distribution of cache entries by time until expiry.
 * Provides statistics on how many entries will expire within various time windows.
 */
public class ExpiryDistribution {
    private final long lessThan1Minute;
    private final long lessThan5Minutes;
    private final long lessThan15Minutes;
    private final long lessThan1Hour;
    private final long lessThan24Hours;
    private final long moreThan24Hours;
    private final long neverExpires;
    private final long total;

    public ExpiryDistribution(
            long lessThan1Minute,
            long lessThan5Minutes,
            long lessThan15Minutes,
            long lessThan1Hour,
            long lessThan24Hours,
            long moreThan24Hours,
            long neverExpires) {
        this.lessThan1Minute = lessThan1Minute;
        this.lessThan5Minutes = lessThan5Minutes;
        this.lessThan15Minutes = lessThan15Minutes;
        this.lessThan1Hour = lessThan1Hour;
        this.lessThan24Hours = lessThan24Hours;
        this.moreThan24Hours = moreThan24Hours;
        this.neverExpires = neverExpires;
        this.total = lessThan1Minute + lessThan5Minutes + lessThan15Minutes +
                     lessThan1Hour + lessThan24Hours + moreThan24Hours + neverExpires;
    }

    public long getLessThan1Minute() {
        return lessThan1Minute;
    }

    public long getLessThan5Minutes() {
        return lessThan5Minutes;
    }

    public long getLessThan15Minutes() {
        return lessThan15Minutes;
    }

    public long getLessThan1Hour() {
        return lessThan1Hour;
    }

    public long getLessThan24Hours() {
        return lessThan24Hours;
    }

    public long getMoreThan24Hours() {
        return moreThan24Hours;
    }

    public long getNeverExpires() {
        return neverExpires;
    }

    public long getTotal() {
        return total;
    }

    public double getPercentage(long count) {
        return total == 0 ? 0.0 : (count * 100.0 / total);
    }

    @Override
    public String toString() {
        if (total == 0) {
            return "No entries in cache";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Expiry Distribution (").append(total).append(" total entries):\n");

        if (lessThan1Minute > 0) {
            sb.append(String.format("  < 1 minute: %d entries (%.1f%%)\n",
                    lessThan1Minute, getPercentage(lessThan1Minute)));
        }
        if (lessThan5Minutes > 0) {
            sb.append(String.format("  < 5 minutes: %d entries (%.1f%%)\n",
                    lessThan5Minutes, getPercentage(lessThan5Minutes)));
        }
        if (lessThan15Minutes > 0) {
            sb.append(String.format("  < 15 minutes: %d entries (%.1f%%)\n",
                    lessThan15Minutes, getPercentage(lessThan15Minutes)));
        }
        if (lessThan1Hour > 0) {
            sb.append(String.format("  < 1 hour: %d entries (%.1f%%)\n",
                    lessThan1Hour, getPercentage(lessThan1Hour)));
        }
        if (lessThan24Hours > 0) {
            sb.append(String.format("  < 24 hours: %d entries (%.1f%%)\n",
                    lessThan24Hours, getPercentage(lessThan24Hours)));
        }
        if (moreThan24Hours > 0) {
            sb.append(String.format("  > 24 hours: %d entries (%.1f%%)\n",
                    moreThan24Hours, getPercentage(moreThan24Hours)));
        }
        if (neverExpires > 0) {
            sb.append(String.format("  Never expires: %d entries (%.1f%%)\n",
                    neverExpires, getPercentage(neverExpires)));
        }

        return sb.toString();
    }
}
