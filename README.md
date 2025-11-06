# Kachi Cache

A high-performance Java cache library inspired by Google Guava and Caffeine, with support for TTL (time-to-live), lazy loading, and excellent concurrent performance.

## Features

- **High Performance**: Thread-safe concurrent cache using `ConcurrentHashMap` with write-priority semantics
- **TTL Support**: Flexible time-based expiration with `expireAfterWrite` and `expireAfterAccess`
- **Lazy Loading**: Automatic value loading with `CacheLoader` for database/API integration
- **Multiple Eviction Policies**: Choose from LRU, LFU, or FIFO when size limit is reached
- **Removal Listeners**: Get notified when entries are removed with the reason (SIZE, EXPIRED, EXPLICIT, REPLACED)
- **Scheduled TTL Cleanup**: Automatic background cleanup of expired entries every minute
- **Write-Priority Locking**: Reads wait up to 1 second for writes to ensure latest data
- **Statistics**: Built-in performance metrics tracking (hit rate, miss rate, load times, etc.)
- **Simple API**: Fluent builder pattern similar to Guava Cache
- **Zero Dependencies**: Pure Java implementation with no external dependencies

## Installation

### Maven

```xml
<dependency>
    <groupId>com.github.rudy</groupId>
    <artifactId>kachi</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Building from Source

```bash
git clone https://github.com/rudygunawan/Kachi.git
cd Kachi
mvn clean install
```

## Quick Start

### Basic Cache Usage

```java
import com.github.rudy.kachi.Cache;
import com.github.rudy.kachi.CacheBuilder;

// Create a simple cache
Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .build();

// Put values
cache.put("user123", user);

// Get values
User user = cache.getIfPresent("user123");

// Get or compute
User user = cache.get("user123", () -> loadUserFromDatabase("user123"));
```

### Loading Cache with Automatic Loading

```java
import com.github.rudy.kachi.LoadingCache;
import com.github.rudy.kachi.CacheLoader;

// Create a loading cache
LoadingCache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(10000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .recordStats()
    .build(new CacheLoader<String, User>() {
        @Override
        public User load(String userId) throws Exception {
            return database.loadUser(userId);
        }
    });

// Automatically loads from database if not cached
User user = cache.get("user123");

// Bulk loading
Map<String, User> users = cache.getAll(Arrays.asList("user1", "user2", "user3"));
```

### TTL (Time-to-Live) Configuration

```java
// Expire entries 5 minutes after write
Cache<String, String> cache = CacheBuilder.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build();

// Expire entries 10 minutes after last access
Cache<String, String> cache = CacheBuilder.newBuilder()
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .build();

// Combine both
Cache<String, String> cache = CacheBuilder.newBuilder()
    .expireAfterWrite(30, TimeUnit.MINUTES)
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .build();
```

### Size-Based Eviction (LRU)

```java
// Cache with maximum 1000 entries, using LRU eviction
Cache<String, String> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .build();

// When cache exceeds 1000 entries, least recently used entries are evicted
```

### Statistics Tracking

```java
import com.github.rudy.kachi.CacheStats;

Cache<String, String> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .recordStats()
    .build();

// Use the cache...
cache.put("key1", "value1");
cache.getIfPresent("key1");
cache.getIfPresent("key2");

// Get statistics
CacheStats stats = cache.stats();
System.out.println("Hit rate: " + stats.hitRate());
System.out.println("Miss rate: " + stats.missRate());
System.out.println("Evictions: " + stats.evictionCount());
System.out.println("Average load time: " + stats.averageLoadPenalty() + " ns");
```

### Eviction Policies

Choose from three eviction strategies when the cache reaches its maximum size:

```java
// LRU (Least Recently Used) - default
Cache<String, String> lruCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .evictionPolicy(EvictionPolicy.LRU)
    .build();

// LFU (Least Frequently Used) - evicts entries with lowest access count
Cache<String, String> lfuCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .evictionPolicy(EvictionPolicy.LFU)
    .build();

// FIFO (First In First Out) - evicts oldest entries first
Cache<String, String> fifoCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .evictionPolicy(EvictionPolicy.FIFO)
    .build();
```

**When to use each policy:**
- **LRU**: Best for most use cases where recently accessed items are more valuable
- **LFU**: Good when some keys are accessed much more frequently than others
- **FIFO**: Simple policy when newer entries are generally more valuable

**Minimum Age Protection:**
All entries must remain in the cache for at least **1 minute** before they can be evicted due to size constraints. This prevents newly added entries from being immediately evicted, ensuring fair cache utilization. This protection applies to:
- LRU eviction
- LFU eviction
- FIFO eviction

Note: Manual invalidation (`invalidate()`) and replacements (`put()` on existing key) are not affected by this minimum age requirement.

### Removal Listeners

Get notified when entries are removed from the cache:

```java
RemovalListener<String, User> listener = (key, value, cause) -> {
    System.out.println("Removed " + key + " because: " + cause);

    // Different actions based on removal cause
    switch (cause) {
        case EXPIRED:
            log.info("Entry expired: " + key);
            break;
        case SIZE:
            metrics.recordEviction(key);
            break;
        case EXPLICIT:
            log.debug("Manually invalidated: " + key);
            break;
        case REPLACED:
            log.debug("Value replaced: " + key);
            break;
    }
};

Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .removalListener(listener)
    .build();
```

**Removal Causes:**
- `EXPIRED`: Entry's TTL expired (expireAfterWrite or expireAfterAccess)
- `SIZE`: Entry evicted due to size limit (uses configured eviction policy)
- `EXPLICIT`: Entry manually removed via `invalidate()` or `invalidateAll()`
- `REPLACED`: Entry's value was replaced by a new `put()` operation

### Scheduled TTL Cleanup

When TTL is configured, Kachi automatically runs a background cleanup task every minute:

```java
Cache<String, String> cache = CacheBuilder.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .removalListener((key, value, cause) -> {
        if (cause == RemovalCause.EXPIRED) {
            System.out.println("Auto-cleaned expired entry: " + key);
        }
    })
    .build();

// Cleanup runs automatically every minute
// You can also trigger manual cleanup:
cache.cleanUp();
```

The scheduled cleanup ensures expired entries are removed proactively, preventing memory buildup and triggering removal listeners for expired entries.

### Write-Priority Concurrency

Kachi uses write-priority locking to ensure reads always get the latest data:

```java
Cache<String, Config> cache = CacheBuilder.newBuilder().build();

// Writer thread
new Thread(() -> {
    cache.put("config", loadLatestConfig());
}).start();

// Reader thread - waits up to 1 second for write to complete
new Thread(() -> {
    Config config = cache.getIfPresent("config");
    // Always gets the latest value or null on timeout
}).start();
```

**Benefits:**
- Writes never wait for reads
- Reads wait up to 1 second for writes, ensuring fresh data
- Prevents stale reads during updates
- Per-key locking for maximum concurrency

### Micrometer Metrics Integration

Kachi integrates with [Micrometer](https://micrometer.io/) to expose comprehensive cache metrics for monitoring and observability:

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

// Create a meter registry (or use your existing one)
MeterRegistry registry = new SimpleMeterRegistry();

// Create and monitor a cache
Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .recordStats()  // Required for metrics
    .build();

// Bind metrics to registry
MicrometerCacheMetrics.monitor(registry, (ConcurrentCacheImpl) cache, "userCache");
```

**Available Metrics:**

| Metric Name | Type | Description |
|-------------|------|-------------|
| `cache.size` | Gauge | Current number of entries in cache |
| `cache.hits` | Counter | Total number of cache hits |
| `cache.misses` | Counter | Total number of cache misses |
| `cache.evictions` | Counter | Total number of evictions |
| `cache.loads` | Counter | Total loads (tagged by result: success/failure) |
| `cache.load.duration` | Timer | Time spent loading values |
| `cache.hit.ratio` | Gauge | Cache hit rate (0.0 to 1.0) |
| `cache.idle.entries` | Gauge | Entries not accessed in last 5 minutes |
| `cache.memory.estimated` | Gauge | Estimated memory usage in bytes |

**With Custom Tags:**

```java
import io.micrometer.core.instrument.Tags;

Tags customTags = Tags.of(
    "application", "myapp",
    "environment", "production"
);

MicrometerCacheMetrics.monitor(registry, cache, "userCache", customTags);
```

**Integration with Monitoring Systems:**

```java
// Prometheus
PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
MicrometerCacheMetrics.monitor(registry, cache, "userCache");

// Expose metrics endpoint
app.get("/metrics", (req, res) -> {
    res.contentType("text/plain");
    res.send(registry.scrape());
});

// Grafana, Datadog, New Relic, etc. - use respective Micrometer registries
```

**Monitoring Dashboard Example:**

```yaml
# Useful queries for monitoring
- Alert on low hit rate: cache_hit_ratio{cache="userCache"} < 0.5
- Track eviction rate: rate(cache_evictions_total[5m])
- Monitor memory usage: cache_memory_estimated_bytes
- Detect idle entries: cache_idle_entries > 100
```

## Advanced Usage

### Database Integration Example

```java
public class UserCache {
    private final LoadingCache<String, User> cache;

    public UserCache(UserRepository repository) {
        this.cache = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .recordStats()
            .build(new CacheLoader<String, User>() {
                @Override
                public User load(String userId) throws Exception {
                    return repository.findById(userId)
                        .orElseThrow(() -> new UserNotFoundException(userId));
                }

                @Override
                public Map<String, User> loadAll(Iterable<? extends String> userIds) throws Exception {
                    return repository.findAllById(userIds);
                }
            });
    }

    public User getUser(String userId) throws Exception {
        return cache.get(userId);
    }

    public void invalidateUser(String userId) {
        cache.invalidate(userId);
    }

    public CacheStats getStats() {
        return cache.stats();
    }
}
```

### Refresh Strategy

```java
LoadingCache<String, String> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .build(loader);

// Asynchronously refresh a value (old value remains until new one loads)
cache.refresh("key");

// Periodic refresh using ScheduledExecutorService
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(() -> {
    for (String key : cache.asMap().keySet()) {
        cache.refresh(key);
    }
}, 0, 10, TimeUnit.MINUTES);
```

### Cleanup and Maintenance

```java
Cache<String, String> cache = CacheBuilder.newBuilder()
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build();

// Manually trigger cleanup of expired entries
cache.cleanUp();

// Get cache as a Map view
Map<String, String> snapshot = cache.asMap();

// Clear all entries
cache.invalidateAll();
```

## Configuration Options

| Method | Description | Default |
|--------|-------------|---------|
| `initialCapacity(int)` | Initial capacity of internal hash table | 16 |
| `concurrencyLevel(int)` | Concurrency level for updates | 4 |
| `maximumSize(long)` | Maximum number of entries | unlimited |
| `expireAfterWrite(long, TimeUnit)` | Expire entries after write time | unlimited |
| `expireAfterAccess(long, TimeUnit)` | Expire entries after access time | unlimited |
| `evictionPolicy(EvictionPolicy)` | Eviction policy (LRU, LFU, FIFO) | LRU |
| `removalListener(RemovalListener)` | Listener for removal events | none |
| `recordStats()` | Enable statistics tracking | disabled |

## Performance

Kachi Cache is designed for high-performance concurrent access. Benchmark results on a modern multi-core system:

- **Single-threaded reads**: ~20-40M ops/sec
- **Concurrent reads** (8 threads): ~100-150M ops/sec
- **Mixed workload** (80% read, 20% write): ~50-80M ops/sec
- **Average latency**: 50-100 nanoseconds per operation

Run benchmarks yourself:

```bash
mvn test -Dtest=PerformanceBenchmark
```

## API Comparison

### Guava Cache

```java
// Guava
LoadingCache<Key, Graph> graphs = CacheBuilder.newBuilder()
    .maximumSize(10000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .recordStats()
    .build(new CacheLoader<Key, Graph>() {
        public Graph load(Key key) {
            return createExpensiveGraph(key);
        }
    });

// Kachi (identical API!)
LoadingCache<Key, Graph> graphs = CacheBuilder.newBuilder()
    .maximumSize(10000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .recordStats()
    .build(new CacheLoader<Key, Graph>() {
        public Graph load(Key key) {
            return createExpensiveGraph(key);
        }
    });
```

Kachi provides a Guava-compatible API for easy migration.

## Architecture

Kachi Cache uses a layered architecture:

1. **Storage Layer**: `ConcurrentHashMap` for thread-safe storage
2. **Entry Management**: `CacheEntry` wraps values with expiration metadata
3. **Eviction**: LRU tracking with `ConcurrentLinkedDeque`
4. **Loading**: `CompletableFuture` prevents duplicate loads
5. **Statistics**: Lock-free atomic counters for minimal overhead

## Requirements

- Java 11 or higher
- No external dependencies

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=CacheTest

# Run benchmarks
mvn test -Dtest=PerformanceBenchmark
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the terms specified in the LICENSE file.

## Related Projects

- [Google Guava](https://github.com/google/guava) - Comprehensive Java library with caching support
- [Caffeine](https://github.com/ben-manes/caffeine) - High-performance caching library for Java

## Why Kachi?

- **Lightweight**: No dependencies, small footprint
- **Performance**: Optimized for concurrent access with minimal locking
- **Simple**: Clean API inspired by industry standards
- **Flexible**: TTL, lazy loading, and eviction policies built-in
- **Educational**: Clear, readable implementation for learning cache internals
