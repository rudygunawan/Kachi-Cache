package com.github.rudygunawan.kachi.policy;

/**
 * Reference strength for cache keys and values.
 *
 * <p>This enum specifies how strongly the cache should hold references to keys and values,
 * allowing integration with Java's garbage collection for memory-sensitive applications.
 *
 * <p><b>Reference Types:</b>
 * <ul>
 *   <li>{@link #STRONG} - Normal strong references (default, no GC)</li>
 *   <li>{@link #WEAK} - Weak references (eligible for GC when no strong refs exist)</li>
 *   <li>{@link #SOFT} - Soft references (eligible for GC under memory pressure)</li>
 * </ul>
 *
 * <p><b>Use Cases:</b>
 * <table border="1">
 *   <tr>
 *     <th>Strength</th>
 *     <th>Use Case</th>
 *     <th>GC Behavior</th>
 *   </tr>
 *   <tr>
 *     <td>STRONG</td>
 *     <td>Default caching</td>
 *     <td>Never collected (unless evicted)</td>
 *   </tr>
 *   <tr>
 *     <td>WEAK</td>
 *     <td>Canonicalizing mappings, identity-based caches</td>
 *     <td>Collected as soon as no strong references exist</td>
 *   </tr>
 *   <tr>
 *     <td>SOFT</td>
 *     <td>Memory-sensitive caches</td>
 *     <td>Collected when JVM needs memory (last resort before OOM)</td>
 *   </tr>
 * </table>
 *
 * <p><b>Note:</b> Weak and soft references are not compatible with all cache features.
 * AsyncCache operations may not work correctly with weak references.
 *
 * @since 1.0.0
 */
public enum Strength {
    /**
     * Strong references - the default behavior. Entries are held strongly and will not be
     * garbage collected unless explicitly evicted or expired.
     */
    STRONG,

    /**
     * Weak references - entries are eligible for garbage collection when there are no strong
     * references to them, even if they are still in the cache.
     *
     * <p>Useful for canonicalizing mappings where the same object should be reused if it exists,
     * but can be garbage collected when no longer needed elsewhere in the application.
     *
     * <p><b>Example use case:</b> Interning strings or canonical object instances.
     */
    WEAK,

    /**
     * Soft references - entries are eligible for garbage collection when the JVM is running
     * low on memory. The GC will prefer to collect soft references before throwing an
     * {@link OutOfMemoryError}.
     *
     * <p>Useful for memory-sensitive caches that can be reconstructed if needed, but should
     * persist as long as memory allows.
     *
     * <p><b>Example use case:</b> Image caches, parsed document caches, computational results.
     */
    SOFT;

    /**
     * Returns {@code true} if this strength allows garbage collection.
     *
     * @return {@code true} for WEAK or SOFT, {@code false} for STRONG
     */
    public boolean isCollectible() {
        return this != STRONG;
    }
}
