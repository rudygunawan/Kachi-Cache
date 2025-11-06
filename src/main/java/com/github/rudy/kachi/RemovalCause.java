package com.github.rudy.kachi;

/**
 * The reason why a cached entry was removed.
 */
public enum RemovalCause {
    /**
     * The entry was manually removed by the user using {@link Cache#invalidate} or
     * {@link Cache#invalidateAll}.
     */
    EXPLICIT,

    /**
     * The entry was removed automatically because its value was replaced by a new value.
     */
    REPLACED,

    /**
     * The entry was removed because it exceeded the size limit of the cache.
     */
    SIZE,

    /**
     * The entry's expiration timestamp has passed (either write time or access time based TTL).
     */
    EXPIRED;

    /**
     * Returns {@code true} if the removal was caused by eviction (either SIZE or EXPIRED),
     * rather than manual removal or replacement.
     */
    public boolean wasEvicted() {
        return this == SIZE || this == EXPIRED;
    }
}
