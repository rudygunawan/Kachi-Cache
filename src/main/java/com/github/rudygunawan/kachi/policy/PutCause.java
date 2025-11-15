package com.github.rudygunawan.kachi.cache.policy;

/**
 * The reason why a cache entry was put/inserted into the cache.
 * <p>
 * This is useful for listeners that need to distinguish between new insertions
 * and updates/replacements of existing entries, particularly for async database
 * upsert operations.
 *
 * @since 1.0.0
 */
public enum PutCause {
    /**
     * The entry is a new insertion - the key did not previously exist in the cache.
     * <p>
     * This is particularly useful for async database operations where you need to
     * INSERT a new record into the database.
     */
    INSERT,

    /**
     * The entry is an update/replacement - the key already existed in the cache
     * and its value was replaced with a new value.
     * <p>
     * This is particularly useful for async database operations where you need to
     * UPDATE an existing record in the database.
     */
    UPDATE;

    /**
     * Returns {@code true} if this put operation created a new entry.
     *
     * @return {@code true} if the cause is INSERT, {@code false} otherwise
     */
    public boolean isNewEntry() {
        return this == INSERT;
    }

    /**
     * Returns {@code true} if this put operation updated an existing entry.
     *
     * @return {@code true} if the cause is UPDATE, {@code false} otherwise
     */
    public boolean isUpdate() {
        return this == UPDATE;
    }
}
