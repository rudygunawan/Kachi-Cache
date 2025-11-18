# Kachi Cache Features

A high-performance, flexible caching library for Java 21+ with dual-strategy architecture.

## Version History

### Version 0.2.0 (Current) - NEW FEATURES ✨

#### 1. AsyncCache / AsyncLoadingCache 🆕
Non-blocking cache operations using CompletableFuture for modern async applications.

```java
// AsyncCache - manual loading
AsyncCache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .buildAsync();

CompletableFuture<User> future = cache.get("userId", key ->
    CompletableFuture.supplyAsync(() -> database.fetchUser(key))
);

// AsyncLoadingCache - automatic loading
AsyncLoadingCache<String, User> loadingCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .buildAsync((key, executor) ->
        CompletableFuture.supplyAsync(() -> database.fetchUser(key), executor)
    );

CompletableFuture<User> user = loadingCache.get("userId");
```

**Features:**
- Non-blocking operations with CompletableFuture
- Virtual threads (JDK 21) for lightweight concurrency
- Automatic value loading with AsyncCacheLoader
- Bulk operations (getAll, putAll)

#### 2. Atomic Compute Operations 🆕
Thread-safe atomic operations for concurrent updates.

```java
Cache<String, Integer> cache = CacheBuilder.newBuilder().build();

// compute - always computes
cache.compute("counter", (k, v) -> (v == null) ? 1 : v + 1);

// computeIfAbsent - only computes if absent
cache.computeIfAbsent("key", k -> expensiveComputation(k));

// computeIfPresent - only computes if present
cache.computeIfPresent("key", (k, v) -> v + "-updated");

// merge - merges new value with existing
cache.merge("counter", 1, Integer::sum);
```

**Operations:**
- `compute(key, remappingFunction)` - Atomic compute for any state
- `computeIfAbsent(key, mappingFunction)` - Lazy initialization
- `computeIfPresent(key, remappingFunction)` - Conditional update
- `merge(key, value, remappingFunction)` - Value merging

#### 3. CacheWriter Interface 🆕
Structured write-through and write-behind patterns for external storage synchronization.

```java
// Write-through pattern
CacheWriter<String, User> writer = CacheWriter.sync(
    (key, value) -> database.upsert(key, value),
    (key, value, cause) -> {
        if (cause == RemovalCause.EXPLICIT) {
            database.delete(key);
        }
    }
);

// Async write-behind pattern
ExecutorService executor = Executors.newFixedThreadPool(4);
CacheWriter<String, User> asyncWriter = CacheWriter.async(
    executor,
    (key, value) -> database.upsert(key, value),
    (key, value, cause) -> database.delete(key)
);

Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .writer(asyncWriter)
    .build();
```

**Features:**
- Separate `write()` and `delete()` methods
- Synchronous and asynchronous factory methods
- Delete called for all removals with cause information
- Exception handling (logged and swallowed)

#### 4. Custom Executor / Scheduler 🆕
Configure custom executors for async operations and schedulers for background tasks.

```java
ExecutorService customExecutor = Executors.newFixedThreadPool(8);
ScheduledExecutorService customScheduler = Executors.newScheduledThreadPool(2);

Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .executor(customExecutor)
    .scheduler(customScheduler)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build();
```

**Use Cases:**
- Custom thread pools for async operations
- Resource management and thread naming
- Performance tuning for specific workloads

#### 5. Reference Strength Configuration 🆕
Configure weak and soft references for GC-aware caching.

```java
// Weak keys - GC when no strong references
Cache<String, User> weakKeyCache = CacheBuilder.newBuilder()
    .weakKeys()
    .build();

// Weak values - GC when no strong references
Cache<String, User> weakValueCache = CacheBuilder.newBuilder()
    .weakValues()
    .build();

// Soft values - GC under memory pressure
Cache<String, User> softValueCache = CacheBuilder.newBuilder()
    .softValues()
    .build();

// Combination
Cache<String, User> cache = CacheBuilder.newBuilder()
    .weakKeys()
    .softValues()
    .build();
```

**Reference Types:**
- `STRONG` - Default, no GC
- `WEAK` - GC when no strong references exist
- `SOFT` - GC under memory pressure

**Note:** Storage layer integration for weak/soft references is reserved for future implementation. Currently, these methods configure the API but use strong references internally.

#### 6. EvictionListener Interface 🆕
Specialized listener for eviction events only (SIZE and EXPIRED removals).

```java
EvictionListener<String, User> listener = (key, value, cause) -> {
    System.out.println("Evicted: " + key + ", cause: " + cause);
    // Cleanup resources, log metrics, etc.
};

Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .evictionListener(listener)
    .build();
```

**Features:**
- Only called for `RemovalCause.SIZE` and `RemovalCause.EXPIRED`
- Not called for `EXPLICIT` or `REPLACED` removals
- Works alongside `RemovalListener`
- Exception handling (logged and swallowed)

#### 7. Policy Introspection API 🆕
Runtime inspection and modification of cache policies (Caffeine-compatible).

```java
Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build();

Policy<String, User> policy = cache.policy();

// Inspect eviction policy
policy.eviction().ifPresent(eviction -> {
    System.out.println("Max size: " + eviction.getMaximum());
    System.out.println("Current size: " + eviction.weightedSize());
    System.out.println("Is weighted: " + eviction.isWeighted());
    System.out.println("Policy: " + eviction.getEvictionPolicy());
});

// Dynamically resize cache
policy.eviction().ifPresent(eviction -> {
    eviction.setMaximum(2000);
});

// Inspect expiration policy
policy.expiration().ifPresent(expiration -> {
    System.out.println("Expire after write: " + expiration.getExpiresAfterWrite());
    System.out.println("Expire after access: " + expiration.getExpiresAfterAccess());

    // Query entry age
    long age = expiration.ageOf("userId");
    System.out.println("Entry age: " + age + " ns");
});

// Dynamically change TTL
policy.expiration().ifPresent(expiration -> {
    expiration.setExpiresAfterWrite(TimeUnit.MINUTES.toNanos(20));
});
```

**Capabilities:**
- **Eviction:** getMaximum(), setMaximum(), weightedSize(), isWeighted(), getEvictionPolicy()
- **Expiration:** getExpiresAfterWrite(), setExpiresAfterWrite(), getExpiresAfterAccess(), setExpiresAfterAccess(), ageOf(key)
- Dynamic runtime modification
- Optional-based API (returns empty if feature not configured)

---

### Version 0.1.0 - Core Features

#### Dual-Strategy Architecture
Two cache implementations optimized for different use cases:

**HIGH_PERFORMANCE (Default):**
- GET: ~60ns (16.75M ops/sec)
- PUT: ~15,978ns (62,587 ops/sec)
- Concurrent: 14.1M ops/sec (16 threads)
- Lock-free reads with ConcurrentHashMap
- Random eviction (not LRU/FIFO)
- Eventual consistency

**PRECISION:**
- GET: ~800-1,400ns (still fast)
- Concurrent: ~1-2M ops/sec
- Accurate LRU/FIFO/LFU/TinyLFU eviction
- Per-key locking with write-priority
- Strong consistency guarantees

```java
// High-performance cache (default)
Cache<String, User> fastCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .build();

// Precision cache (accurate eviction)
Cache<String, User> preciseCache = CacheBuilder.newBuilder()
    .strategy(CacheStrategy.PRECISION)
    .maximumSize(1000)
    .build();
```

#### Size-Based Eviction
Control cache size with entry count or custom weights.

```java
// Entry-based size limit
Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .build();

// Weight-based size limit
Cache<String, User> weightedCache = CacheBuilder.newBuilder()
    .maximumWeight(10_000)
    .weigher((key, value) -> value.getSize())
    .build();
```

#### Eviction Policies
Multiple eviction algorithms for different access patterns:

```java
Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .evictionPolicy(EvictionPolicy.LRU)        // Least Recently Used
    .evictionPolicy(EvictionPolicy.LFU)        // Least Frequently Used
    .evictionPolicy(EvictionPolicy.FIFO)       // First In First Out
    .evictionPolicy(EvictionPolicy.WINDOW_TINY_LFU) // Window TinyLFU (admission policy)
    .build();
```

#### Time-Based Expiration
Automatic entry expiration with fixed or custom policies.

```java
// Fixed TTL after write
Cache<String, User> cache = CacheBuilder.newBuilder()
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build();

// Fixed TTL after access
Cache<String, User> cache = CacheBuilder.newBuilder()
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .build();

// Custom per-entry TTL
Expiry<String, User> customExpiry = new Expiry<>() {
    @Override
    public long expireAfterCreate(String key, User value, long currentTime) {
        return value.isPremium()
            ? TimeUnit.HOURS.toNanos(24)
            : TimeUnit.HOURS.toNanos(1);
    }
};

Cache<String, User> cache = CacheBuilder.newBuilder()
    .expireAfter(customExpiry)
    .build();
```

#### LoadingCache
Automatic value loading with configurable loaders.

```java
CacheLoader<String, User> loader = key -> database.fetchUser(key);

LoadingCache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .build(loader);

// Automatic loading on miss
User user = cache.get("userId");
```

#### Refresh Policies
Background refresh with time-based or custom policies.

```java
// Fixed refresh interval
LoadingCache<String, User> cache = CacheBuilder.newBuilder()
    .refreshAfterWrite(5, TimeUnit.MINUTES)
    .build(loader);

// Time-based refresh policy (active during business hours)
RefreshPolicy<String, Data> policy = new TimeBasedRefreshPolicy<>(ZoneId.of("America/New_York"))
    .addActiveWindow(9, 30, 16, 0, 1, TimeUnit.MINUTES)  // Market hours
    .setDefaultInterval(10, TimeUnit.MINUTES);

LoadingCache<String, Data> cache = CacheBuilder.newBuilder()
    .refreshAfter(policy)
    .build(loader);
```

#### RemovalListener
Listen to all cache removal events.

```java
RemovalListener<String, User> listener = (key, value, cause) -> {
    System.out.println("Removed: " + key + ", cause: " + cause);
};

Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .removalListener(listener)
    .build();
```

**Removal Causes:**
- `EXPLICIT` - User called invalidate()
- `REPLACED` - Entry replaced by put()
- `SIZE` - Evicted due to size constraints
- `EXPIRED` - Expired due to TTL

#### PutListener
Listen to cache put operations with INSERT/UPDATE distinction.

```java
PutListener<String, User> listener = (key, value, cause) -> {
    if (cause == PutCause.INSERT) {
        System.out.println("New entry: " + key);
        // Async database insert
    } else {
        System.out.println("Updated entry: " + key);
        // Async database update
    }
};

Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .putListener(listener)
    .build();
```

#### Statistics
Track cache performance metrics.

```java
Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .recordStats()
    .build();

CacheStats stats = cache.stats();
System.out.println("Hit rate: " + stats.hitRate());
System.out.println("Miss rate: " + stats.missRate());
System.out.println("Eviction count: " + stats.evictionCount());
System.out.println("Average load penalty: " + stats.averageLoadPenalty());
```

#### Concurrency Control
Fine-tune concurrent access.

```java
Cache<String, User> cache = CacheBuilder.newBuilder()
    .initialCapacity(100)
    .concurrencyLevel(16)
    .build();
```

---

## Feature Comparison

| Feature | Kachi 0.1.0 | Kachi 0.2.0 | Caffeine | Guava Cache |
|---------|-------------|-------------|----------|-------------|
| **Core Features** |
| Size-based eviction | ✅ | ✅ | ✅ | ✅ |
| Time-based expiration | ✅ | ✅ | ✅ | ✅ |
| LoadingCache | ✅ | ✅ | ✅ | ✅ |
| Statistics | ✅ | ✅ | ✅ | ✅ |
| RemovalListener | ✅ | ✅ | ✅ | ✅ |
| **New in 0.2.0** |
| AsyncCache / AsyncLoadingCache | ❌ | ✅ | ✅ | ❌ |
| Compute operations | ❌ | ✅ | ✅ | ❌ |
| CacheWriter | ❌ | ✅ | ✅ | ❌ |
| Custom Executor/Scheduler | ❌ | ✅ | ✅ | ❌ |
| Weak/Soft references | ❌ | 🔶 API | ✅ | ✅ |
| EvictionListener | ❌ | ✅ | ❌ | ❌ |
| Policy introspection | ❌ | ✅ | ✅ | ❌ |
| **Kachi Unique** |
| PutListener (INSERT/UPDATE) | ✅ | ✅ | ❌ | ❌ |
| Dual-strategy architecture | ✅ | ✅ | ❌ | ❌ |
| Time-based refresh policy | ✅ | ✅ | ❌ | ❌ |
| Virtual threads (JDK 21) | ✅ | ✅ | ❌ | ❌ |

Legend:
- ✅ Fully implemented
- 🔶 API only (storage layer pending)
- ❌ Not available

---

## Performance Benchmarks

### Kachi Cache (HIGH_PERFORMANCE Strategy)
- **GET:** ~60ns (16.75M ops/sec)
- **PUT:** ~15,978ns (62,587 ops/sec)
- **Concurrent (16 threads):** 14.1M ops/sec

### Kachi Cache (PRECISION Strategy)
- **GET:** ~800-1,400ns
- **Concurrent:** ~1-2M ops/sec

### Comparison with Caffeine
- **Kachi GET:** 4.7-7.1x faster in concurrent scenarios
- **Caffeine GET:** ~200ns (5M ops/sec)
- **Caffeine Concurrent:** ~2-3M ops/sec

---

## Migration Guide

### From 0.1.0 to 0.2.0

All existing 0.1.0 APIs remain compatible. New features can be adopted incrementally:

```java
// 0.1.0 code - still works
Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build();

// 0.2.0 enhancements - add incrementally
Cache<String, User> enhanced = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .evictionListener((k, v, c) -> logEviction(k, v, c))  // NEW
    .writer(CacheWriter.async(executor, writeAction, deleteAction))  // NEW
    .build();

// Use new features
Policy<String, User> policy = enhanced.policy();  // NEW
policy.eviction().ifPresent(e -> e.setMaximum(2000));  // NEW

Integer result = enhanced.compute("counter", (k, v) -> v == null ? 1 : v + 1);  // NEW
```

---

## Requirements

- **Java:** 21 or higher
- **Dependencies:** None (zero external dependencies)

---

## License

Apache License 2.0
