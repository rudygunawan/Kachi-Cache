# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Kachi Cache** is a high-performance Java cache library inspired by Google Guava and Caffeine. It requires **JDK 21** and leverages modern Java features (virtual threads, records) for optimal performance. The project is currently at version 0.2.0.

Key distinguishing features:
- Dual cache strategies: **HighPerformance** (default, lock-free, ~59ns GET) and **Precision** (strict eviction policies)
- Variable TTL with per-entry expiration logic
- Time-based refresh policies (e.g., stock market trading hours)
- Window TinyLFU eviction algorithm
- Weak/soft reference support for keys and values
- Zero dependencies (except Micrometer for optional metrics)

## Build and Test Commands

### Building
```bash
# Clean and build
mvn clean install

# Compile only
mvn compile

# Build with preview features enabled (required for JDK 21)
mvn clean install -Dtest=skip
```

### Testing
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=CacheTest

# Run specific test method
mvn test -Dtest=CacheTest#testBasicGetAndPut

# Run performance benchmarks
mvn test -Dtest=PerformanceBenchmark

# Run strategy comparison benchmarks
mvn test -Dtest=QuickPerformanceSnapshot
mvn test -Dtest=PrecisionPerformanceSnapshot
```

### Running Examples
```bash
# Run a specific example (e.g., WeigherExample)
mvn exec:java -Dexec.mainClass="com.github.rudygunawan.kachi.example.WeigherExample"

# Run stock market refresh example
mvn exec:java -Dexec.mainClass="com.github.rudygunawan.kachi.example.SingaporeStockRefreshExample"
```

## Architecture

### Package Structure

- **`api/`** - Public interfaces (Cache, LoadingCache, AsyncCache, CacheLoader, Expiry, RefreshPolicy, Weigher)
- **`impl/`** - Implementation classes:
  - `HighPerformanceCacheImpl` - Fast cache with random eviction
  - `PrecisionCacheImpl` - Strict policy-based eviction (LRU, FIFO, LFU, TinyLFU)
  - `ConcurrentCacheImpl` - Base implementation (legacy)
  - `AsyncCacheImpl` / `AsyncLoadingCacheImpl` - Async variants
- **`builder/`** - `CacheBuilder` - Fluent API for cache construction
- **`model/`** - Data classes:
  - `CacheEntry` - Full-featured entry with AtomicLong for access tracking
  - `FastCacheEntry` - Minimal entry with volatile long (for HighPerformance)
  - `CacheStats` - JDK 21 record for statistics
- **`policy/`** - Eviction and expiration policies:
  - `EvictionPolicy` - Enum for LRU, FIFO, LFU, WINDOW_TINY_LFU
  - `FrequencySketch` - Count-Min Sketch for TinyLFU
  - `TimeBasedRefreshPolicy` - Time window-based refresh
  - `RemovalCause`, `PutCause` - Event reason enums
  - `Strength` - Enum for weak/soft/strong references
- **`listener/`** - Event listeners (RemovalListener, EvictionListener, PutListener, CacheWriter)
- **`metrics/`** - Micrometer integration
- **`reference/`** - Reference wrappers (KeyReference, ValueReference)
- **`example/`** - Usage examples

### Dual Strategy Design

The codebase implements two distinct cache strategies with identical APIs:

1. **HighPerformance (default)**:
   - Uses `FastCacheEntry` (volatile fields, no atomic operations)
   - Random eviction sampling (no queue maintenance)
   - Deferred eviction checks (batched every 100 PUTs)
   - Allows 5% size overflow for eventual consistency
   - Performance: ~59ns GET, 14M+ concurrent ops/sec

2. **Precision**:
   - Uses `CacheEntry` (AtomicLong for tracking)
   - Maintains access/write queues (LinkedDeque)
   - Strict LRU/FIFO/LFU/TinyLFU eviction
   - Immediate size limit enforcement
   - Performance: ~280ns GET (still fast, but priority is correctness)

Switch via `.strategy(CacheStrategy.HIGH_PERFORMANCE)` or `.strategy(CacheStrategy.PRECISION)` in the builder.

### Key Architectural Patterns

1. **Virtual Threads for I/O** (JDK 21):
   - LoadingCache uses virtual threads for parallel loads
   - Background refresh and cleanup schedulers use virtual threads
   - No thread pool limits for `getAll()` bulk loading

2. **Write-Priority Locking**:
   - Per-key StampedLock (read-optimistic, write-priority)
   - Reads wait up to 1 second for writes to ensure fresh data
   - Located in: `ConcurrentCacheImpl` line ~200-300

3. **Window TinyLFU Algorithm**:
   - 1% window queue (admission), 20% probation, 80% protected
   - Count-Min Sketch for frequency tracking (4 rows, 2048 counters each)
   - Implemented in: `PrecisionCacheImpl` and `FrequencySketch`

4. **Scheduled TTL Cleanup**:
   - Background task runs every 60 seconds
   - Scans all entries, removes expired ones
   - Triggers RemovalListener with `RemovalCause.EXPIRED`

5. **Minimum Age Protection**:
   - Entries must exist for 1 second before size-based eviction
   - Prevents immediate eviction of newly added entries
   - Does not apply to manual invalidation or TTL expiration

## Important Implementation Details

### Cache Entry Lifecycle

1. **Creation**: Entry created via `put()` or `load()`, assigned creation timestamp
2. **Access**: Updates `accessTime` (volatile in Fast, AtomicLong in Precision)
3. **Eviction**: Triggered by size limit, TTL expiration, or manual invalidation
4. **Removal**: Calls RemovalListener with cause (SIZE, EXPIRED, EXPLICIT, REPLACED)

### Eviction Triggers

- **Size-based**: When cache exceeds `maximumSize` or `maximumWeight`
- **Time-based**: TTL via `expireAfterWrite` or `expireAfterAccess`
- **Custom**: Per-entry TTL via `Expiry` interface
- **Manual**: `invalidate()`, `invalidateAll()`

### Loading and Refresh Strategies

- **Synchronous Load**: `cache.get(key, loader)` - blocks until loaded
- **Async Load**: `asyncCache.get(key, asyncLoader)` - returns CompletableFuture
- **Bulk Load**: `cache.getAll(keys)` - parallel loading with virtual threads
- **Background Refresh**: `refreshAfterWrite()` or custom `RefreshPolicy`
- **Time-based Refresh**: `TimeBasedRefreshPolicy` with multiple `TimeWindow` periods

### Statistics Tracking

Enabled via `.recordStats()`:
- Atomic counters (LongAdder) for thread-safe updates
- Minimal overhead (~5-10ns per operation)
- Snapshot via `cache.stats()` returns immutable `CacheStats` record

## Common Development Patterns

### Adding a New Feature to Both Strategies

When adding features that affect both HighPerformance and Precision:

1. Add the API to `Cache.java` or `LoadingCache.java`
2. Implement in `HighPerformanceCacheImpl.java` (optimize for speed)
3. Implement in `PrecisionCacheImpl.java` (optimize for correctness)
4. Add builder method to `CacheBuilder.java`
5. Write tests for both strategies in test class
6. Update README.md with usage example

### Testing Both Strategies

Use parameterized tests to verify both strategies:
```java
@ParameterizedTest
@EnumSource(CacheStrategy.class)
void testFeature(CacheStrategy strategy) {
    Cache<K, V> cache = CacheBuilder.newBuilder()
        .strategy(strategy)
        .maximumSize(100)
        .build();
    // Test logic here
}
```

### Performance Testing

- Benchmarks live in `src/test/java/com/github/rudygunawan/kachi/benchmark/`
- Use JMH-style manual timing (not actual JMH to avoid dependencies)
- Warm up for 100K operations before measuring
- Compare against Guava/Caffeine when relevant

## Configuration Defaults

- `initialCapacity`: 16
- `concurrencyLevel`: 4
- `maximumSize`: unlimited (if not set)
- `evictionPolicy`: LRU (for Precision), random (for HighPerformance)
- `strategy`: HIGH_PERFORMANCE
- `recordStats`: false (disabled by default)
- TTL cleanup interval: 60 seconds
- Minimum age before eviction: 1 second

## Key Files to Reference

- `src/main/java/com/github/rudygunawan/kachi/impl/HighPerformanceCacheImpl.java` - Fast cache implementation
- `src/main/java/com/github/rudygunawan/kachi/impl/PrecisionCacheImpl.java` - Policy-based cache implementation
- `src/main/java/com/github/rudygunawan/kachi/builder/CacheBuilder.java` - Builder with all configuration options
- `src/main/java/com/github/rudygunawan/kachi/policy/FrequencySketch.java` - Count-Min Sketch for TinyLFU
- `docs/CACHE_STRATEGY_COMPARISON.md` - Detailed performance and feature comparison
- `docs/JDK21_FEATURES.md` - How virtual threads and records are used

## Code Style Notes

- JDK 21 features encouraged: records, virtual threads, pattern matching
- Guava-compatible API naming (e.g., `getIfPresent`, `invalidate`, `maximumSize`)
- Thread-safety is paramount - all public methods must be thread-safe
- Performance annotations: Use `// Performance: ~XXXns` comments for critical paths
- Use `java.util.logging` for internal logging (logger name: `com.github.rudygunawan.kachi.Cache`)
