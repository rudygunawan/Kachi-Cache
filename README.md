# Kachi Cache

**Kachi** (カチ) - _pronounced "kah-chee"_

A high-performance Java cache library inspired by Google Guava and Caffeine, with support for TTL (time-to-live), lazy loading, and excellent concurrent performance.


<img src="/images/logo.png" alt="logo" width="300"/>


## About the Name

**Kachi** (カチ) is a Japanese onomatopoeia that captures the essence of this library:

🔊 **Sound**: In Japanese, "カチ" (kachi) represents sharp, crisp sounds:
- The **click** of a switch being flipped
- The **snap** of something locking into place instantly
- The **tick** of a clock marking precise moments

⚡ **Speed**: These sounds embody the library's core promise:
- **Lightning-fast** cache operations (50-100 nanoseconds per operation)
- **Instant** retrieval with O(1) lookups
- **Snap-quick** concurrent access without blocking

🎭 **Wordplay**: The name creates a delightful double meaning:
- **Kachi** (カチ) sounds identical to **"cache"** when pronounced
- A linguistic bridge between Japanese efficiency and caching technology
- Easy to remember: "Kachi Cache" - the cache that clicks!

💡 **Philosophy**: Like the satisfying "kachi" sound of a well-designed mechanism, this library aims to provide:
- **Precision**: Clean, predictable caching behavior
- **Reliability**: Rock-solid concurrent performance
- **Satisfaction**: That "just works" feeling when your cache operations snap into place

In Japanese culture, onomatopoeia (擬音語 _giongo_) are deeply embedded in the language, often conveying more nuance than their English counterparts. "Kachi" isn't just a sound—it's the feeling of certainty, the confidence of something working exactly as it should, _perfectly timed_.

**Fun fact**: In Japanese, "勝ち" (_kachi_) also means "victory" or "win"—a fitting name for a cache library that helps you win the performance battle! 🏆

## Features

- **High Performance**: Thread-safe concurrent cache using `ConcurrentHashMap` with write-priority semantics
- **TTL Support**: Flexible time-based expiration with `expireAfterWrite` and `expireAfterAccess`
- **Per-Entry Expiration**: Variable TTL where different entries can have different expiration times
- **Lazy Loading**: Automatic value loading with `CacheLoader` for database/API integration
- **Advanced Eviction Policies**: Window TinyLFU (near-optimal hit rates), LRU, LFU, or FIFO
- **Weight-Based Eviction**: Control memory usage by entry weight (perfect for variable-size entries)
- **Optimized Bulk Operations**: `getAllPresent()`, `putAll()`, `invalidateAll()`, and parallel `getAll()`
- **Refresh Ahead**: Automatic background refresh with time-based policies (e.g., stock market hours)
- **Removal Listeners**: Get notified when entries are removed with the reason (SIZE, EXPIRED, EXPLICIT, REPLACED)
- **Scheduled TTL Cleanup**: Automatic background cleanup of expired entries every minute
- **Write-Priority Locking**: Reads wait up to 1 second for writes to ensure latest data
- **Statistics**: Built-in performance metrics tracking (hit rate, miss rate, load times, etc.)
- **Simple API**: Fluent builder pattern similar to Guava Cache
- **Zero Dependencies**: Pure Java implementation with no external dependencies
- **Configurable Logging**: Uses java.util.logging for zero-dependency, flexible log configuration

## Installation

### Maven

```xml
<dependency>
    <groupId>com.github.rudygunawan</groupId>
    <artifactId>kachi</artifactId>
    <version>0.1.0</version>
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
import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;

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
import com.github.rudygunawan.kachi.api.LoadingCache;
import com.github.rudygunawan.kachi.api.CacheLoader;

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

### Bulk Operations

Kachi provides optimized bulk operations for working with multiple entries efficiently. Bulk operations are faster than individual operations because they reduce method call overhead and enable parallel processing.

#### getAllPresent - Batch Retrieval Without Loading

Retrieve multiple cached entries without triggering loads:

```java
Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .build();

// Populate cache
cache.put("user1", user1);
cache.put("user2", user2);
cache.put("user3", user3);

// Get multiple entries at once (only returns cached entries)
List<String> keys = Arrays.asList("user1", "user2", "user3", "user999");
Map<String, User> results = cache.getAllPresent(keys);
// Returns: {user1=..., user2=..., user3=...}
// user999 is missing (not loaded)
```

#### putAll - Batch Insert

Insert multiple entries efficiently:

```java
Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(10000)
    .build();

// Prepare batch data
Map<String, User> batchData = new HashMap<>();
for (int i = 1; i <= 1000; i++) {
    batchData.put("user" + i, new User("user" + i, "User " + i));
}

// Insert all at once
cache.putAll(batchData);  // More efficient than 1000 individual put() calls
```

**Performance**: ~2x faster than individual `put()` calls for large batches.

#### invalidateAll - Batch Removal

Remove multiple entries efficiently:

```java
Cache<String, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .build();

// Remove specific keys
List<String> keysToRemove = Arrays.asList("user1", "user2", "user3");
cache.invalidateAll(keysToRemove);

// Or remove all entries
cache.invalidateAll();
```

**Performance**: ~1.3x faster than individual `invalidate()` calls for large batches.

#### getAll - Parallel Batch Loading

For `LoadingCache`, `getAll()` loads missing entries in parallel:

```java
LoadingCache<Integer, User> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .build(new CacheLoader<Integer, User>() {
        @Override
        public User load(Integer id) throws Exception {
            return database.loadUser(id);  // 50ms per query
        }
    });

// Load 10 users (some cached, some not)
List<Integer> userIds = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
Map<Integer, User> users = cache.getAll(userIds);

// If 7 users need loading:
//   Sequential: ~350ms (7 × 50ms)
//   Parallel:   ~50ms (all loads in parallel)
```

**Performance**: Up to Nx faster for N parallel loads (network/database bound operations).

**Use cases for bulk operations:**
- **Batch data imports**: Insert thousands of records efficiently
- **Multi-key lookups**: Retrieve user profiles for a list of IDs
- **Cache warming**: Pre-populate cache with frequently accessed data
- **Session cleanup**: Remove expired sessions in bulk
- **Multi-tenant operations**: Load data for multiple tenants at once

See `com.github.rudygunawan.kachi.example.BulkOperationsExample` for detailed performance comparisons and examples.

### Per-Entry Expiration (Variable TTL)

Different cache entries can have different expiration times based on custom logic:

```java
import com.github.rudygunawan.kachi.api.Expiry;

// Premium users get 2-hour cache, regular users get 30-minute cache
Cache<String, User> cache = CacheBuilder.newBuilder()
    .expireAfter(new Expiry<String, User>() {
        @Override
        public long expireAfterCreate(String key, User user, long currentTime) {
            if (user.isPremium()) {
                return TimeUnit.HOURS.toNanos(2);  // 2 hours for premium
            } else {
                return TimeUnit.MINUTES.toNanos(30);  // 30 minutes for regular
            }
        }

        @Override
        public long expireAfterUpdate(String key, User user, long currentTime, long currentDuration) {
            // Reset TTL on update
            return expireAfterCreate(key, user, currentTime);
        }

        @Override
        public long expireAfterRead(String key, User user, long currentTime, long currentDuration) {
            // Keep existing TTL on read
            return currentDuration;
        }
    })
    .build();
```

**Use cases:**
- **User tiers**: Premium users get longer cache times
- **Data priority**: Important data cached longer
- **Content type**: Images cached longer than JSON responses
- **Time-based**: Cache longer during off-peak hours
- **Key patterns**: Different TTL for different key prefixes

**Example - Priority-based expiration:**

```java
// High priority: 1 hour, Medium: 30 min, Low: 10 min
Cache<String, Document> cache = CacheBuilder.newBuilder()
    .expireAfter(new Expiry<String, Document>() {
        @Override
        public long expireAfterCreate(String key, Document doc, long currentTime) {
            switch (doc.getPriority()) {
                case HIGH:   return TimeUnit.HOURS.toNanos(1);
                case MEDIUM: return TimeUnit.MINUTES.toNanos(30);
                case LOW:    return TimeUnit.MINUTES.toNanos(10);
                default:     return TimeUnit.MINUTES.toNanos(5);
            }
        }
        // ... implement other methods
    })
    .build();
```

**Note:** Custom expiry takes precedence over fixed `expireAfterWrite` TTL.

### Refresh Ahead with Time-Based Policies

Automatically refresh cache entries in the background with different refresh rates for different time periods. Perfect for data with predictable activity patterns:

```java
import com.github.rudygunawan.kachi.policy.TimeWindow;
import com.github.rudygunawan.kachi.policy.TimeBasedRefreshPolicy;

// Singapore Stock Exchange: Morning session (9-12), Afternoon session (1-5)
TimeWindow morningSession = TimeWindow.builder()
    .startTime(9, 0)
    .endTime(12, 0)
    .refreshEvery(10, TimeUnit.SECONDS)
    .build();

TimeWindow afternoonSession = TimeWindow.builder()
    .startTime(13, 0)   // 1:00 PM
    .endTime(17, 0)     // 5:00 PM
    .refreshEvery(10, TimeUnit.SECONDS)
    .build();

List<TimeWindow> tradingHours = Arrays.asList(morningSession, afternoonSession);

TimeBasedRefreshPolicy<String, StockPrice> refreshPolicy =
    new TimeBasedRefreshPolicy<>(
        ZoneId.of("Asia/Singapore"),
        tradingHours,
        120, TimeUnit.SECONDS  // After-hours: refresh every 2 minutes
    );

LoadingCache<String, StockPrice> cache = CacheBuilder.newBuilder()
    .refreshAfter(refreshPolicy)
    .build(ticker -> loadStockPriceFromExchange(ticker));

// Cache automatically refreshes:
//   - Every 10 seconds during trading hours (9am-12pm, 1pm-5pm)
//   - Every 2 minutes after hours
//   - Old value served while new value loads (non-blocking)
```

**Key features:**
- **Multiple time windows**: Support for trading sessions with breaks
- **Timezone-aware**: Specify `ZoneId` for global markets
- **Overlap validation**: Prevents conflicting time windows at construction time
- **Non-blocking refresh**: Old value served while loading new value
- **Graceful failure**: Keeps old value if refresh fails

**Use cases:**
- **Stock market data**: Frequent refresh during trading hours, rare refresh after hours
- **Business hours**: Active refresh 9am-5pm, minimal refresh at night
- **Regional content**: Different refresh rates based on local timezone
- **Event-based**: Higher refresh rate during live events

**Example - NASDAQ trading hours:**

```java
// NASDAQ: 9:30am-4pm EST, pre-market 4am-9:30am, after-hours 4pm-8pm
TimeWindow preMarket = TimeWindow.builder()
    .startTime(4, 0).endTime(9, 30)
    .refreshEvery(1, TimeUnit.MINUTES).build();

TimeWindow marketHours = TimeWindow.builder()
    .startTime(9, 30).endTime(16, 0)
    .refreshEvery(10, TimeUnit.SECONDS).build();

TimeWindow afterHours = TimeWindow.builder()
    .startTime(16, 0).endTime(20, 0)
    .refreshEvery(1, TimeUnit.MINUTES).build();

TimeBasedRefreshPolicy<String, StockPrice> policy =
    new TimeBasedRefreshPolicy<>(
        ZoneId.of("America/New_York"),
        Arrays.asList(preMarket, marketHours, afterHours),
        5, TimeUnit.MINUTES  // Overnight: every 5 minutes
    );
```

**Overlap detection:**

```java
// This will throw IllegalArgumentException due to overlap
TimeWindow morning = TimeWindow.builder()
    .startTime(9, 0).endTime(12, 0)
    .refreshEvery(10, TimeUnit.SECONDS).build();

TimeWindow overlapping = TimeWindow.builder()
    .startTime(11, 0).endTime(14, 0)  // Overlaps with morning!
    .refreshEvery(5, TimeUnit.SECONDS).build();

// IllegalArgumentException: Time windows overlap: 09:00-12:00 and 11:00-14:00
new TimeBasedRefreshPolicy<>(
    ZoneId.systemDefault(),
    Arrays.asList(morning, overlapping),
    30, TimeUnit.SECONDS
);
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
import com.github.rudygunawan.kachi.model.CacheStats;

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

Choose from four eviction strategies when the cache reaches its maximum size:

```java
// Window TinyLFU - Caffeine's advanced algorithm (RECOMMENDED)
// Provides near-optimal hit rates with 10-30% improvement over LRU
Cache<String, String> tinyLfuCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .evictionPolicy(EvictionPolicy.WINDOW_TINY_LFU)
    .recordStats()
    .build();

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
- **Window TinyLFU** (⭐ **RECOMMENDED**): Best for production workloads
  - Near-optimal hit rates (10-30% better than LRU)
  - Scan resistant (large sequential scans don't evict hot items)
  - Adapts to both frequency and recency patterns
  - Ideal for: APIs, databases, CDNs, mixed workloads
- **LRU**: Good general-purpose policy for simple use cases
- **LFU**: Good when some keys are accessed much more frequently than others
- **FIFO**: Simple policy when newer entries are generally more valuable

**Window TinyLFU Performance:**

Window TinyLFU combines:
- **Window Queue** (1%): Admission area for new entries
- **Probation Queue** (20%): Infrequently accessed entries
- **Protected Queue** (80%): Frequently accessed entries
- **Frequency Sketch**: Probabilistic frequency tracking with Count-Min Sketch

Example benchmark showing TinyLFU advantages:

```java
// See: com.github.rudygunawan.kachi.example.WindowTinyLfuExample
// Scenario: 80/20 workload (80% of accesses to 20% of items)
// Results:
//   LRU Hit Rate:     67.42%
//   TinyLFU Hit Rate: 82.15%
//   Improvement:      +14.73 percentage points (22% better)
```

**Minimum Age Protection:**
All entries must remain in the cache for at least **1 second** before they can be evicted due to size constraints. This prevents newly added entries from being immediately evicted, ensuring fair cache utilization. This protection applies to all eviction policies.

Note: Manual invalidation (`invalidate()`) and replacements (`put()` on existing key) are not affected by this minimum age requirement.

### Weight-Based Eviction (Variable-Size Entries)

For caches with variable-size entries (e.g., images, documents, large strings), use weight-based eviction instead of simple entry counting:

```java
import com.github.rudygunawan.kachi.api.Weigher;

// Byte array cache limited by total size (10MB)
Cache<String, byte[]> imageCache = CacheBuilder.newBuilder()
    .maximumWeight(10_000_000)  // 10MB total
    .weigher(Weigher.byteArrayWeigher())  // Weight = array length
    .evictionPolicy(EvictionPolicy.LRU)
    .build();

// Small image: 100KB
imageCache.put("thumbnail.png", new byte[100_000]);

// Large image: 5MB
imageCache.put("banner.jpg", new byte[5_000_000]);

// When total weight exceeds 10MB, entries are evicted based on policy
```

**Built-in Weighers:**

```java
// 1. Singleton weigher (weight = 1 for all entries)
// Equivalent to maximumSize
Weigher.singletonWeigher()

// 2. Byte array weigher (weight = array length)
// Perfect for images, files, binary data
Weigher.byteArrayWeigher()

// 3. String weigher (weight = key.length + value.length)
// Good for text caches with varying sizes
Weigher.stringWeigher()

// 4. Value string weigher (weight = value.length only)
// When keys are small or fixed size
Weigher.valueStringWeigher()
```

**Custom Weighers:**

```java
// Custom weigher for documents
Cache<String, Document> cache = CacheBuilder.<String, Document>newBuilder()
    .maximumWeight(1_000_000)  // 1MB of documents
    .weigher((Weigher<String, Document>) (key, doc) -> {
        // Estimate memory footprint
        int contentSize = doc.getContent() != null ? doc.getContent().length() * 2 : 0;
        int dataSize = doc.getData() != null ? doc.getData().length : 0;
        return contentSize + dataSize + 100;  // + overhead estimate
    })
    .build();
```

**maximumSize vs maximumWeight:**

```java
// Scenario: Storing 100 small items (1KB each) + 1 huge item (10MB)

// Option 1: Entry count limit (maximumSize = 10)
// Problem: Only 10 items fit, wastes potential memory
Cache<String, byte[]> cache1 = CacheBuilder.newBuilder()
    .maximumSize(10)
    .build();

// Option 2: Weight limit (maximumWeight = 100MB)
// Better: Stores ~100 small items OR ~10 huge items, uses memory efficiently
Cache<String, byte[]> cache2 = CacheBuilder.newBuilder()
    .maximumWeight(100_000_000)
    .weigher(Weigher.byteArrayWeigher())
    .build();
```

**Use cases:**
- **Image/file caches**: Control total memory usage, not just count
- **Document stores**: Variable-size documents with consistent memory limits
- **API response caching**: Small JSON vs large binary responses
- **Multi-tenant caches**: Weight by tenant size for fair resource allocation
- **Mixed content types**: Text, images, and data with different sizes

**Important notes:**
- Weigher function must be fast (called on every put)
- Weight must be positive and deterministic for same key/value
- Weight should remain stable over time (don't use current time)
- Can combine with any eviction policy (LRU, TinyLFU, etc.)

See `com.github.rudygunawan.kachi.example.WeigherExample` for comprehensive examples.

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

### Logging Configuration

Kachi uses **`java.util.logging`** (JUL) for internal logging, following the same approach as Google Guava and Caffeine. This keeps the library **dependency-free** while providing flexible logging control.

**Logger name:** `com.github.rudygunawan.kachi.Cache`

#### What Gets Logged

| Level | What's Logged | When to Use |
|-------|---------------|-------------|
| **WARNING** | Errors in custom policies/listeners (operations continue with fallback) | ✅ **Production (recommended)** |
| **FINE** | Evictions, background refreshes | 🔍 **Debugging/Troubleshooting** |
| **OFF** | Nothing | 🚫 **Performance testing** |

#### Quick Configuration

**Option 1: Programmatic (recommended)**

```java
import java.util.logging.Logger;
import java.util.logging.Level;

// Production: Only log errors (default)
Logger.getLogger("com.github.rudygunawan.kachi.Cache").setLevel(Level.WARNING);

// Debug: See evictions and refreshes
Logger.getLogger("com.github.rudygunawan.kachi.Cache").setLevel(Level.FINE);

// Silent: No logging at all
Logger.getLogger("com.github.rudygunawan.kachi.Cache").setLevel(Level.OFF);
```

**Option 2: Using logging.properties file**

```properties
# Create logging.properties file
com.github.rudygunawan.kachi.Cache.level=WARNING

# Run your application
java -Djava.util.logging.config.file=logging.properties YourApp
```

**Option 3: Quick helper methods**

```java
import com.github.rudygunawan.kachi.example.LoggingConfigurationExample.QuickSetup;

// Quick setups for common scenarios
QuickSetup.production();  // WARNING level
QuickSetup.debug();       // FINE level
QuickSetup.silent();      // OFF level
```

#### Integration with SLF4J

If your application uses SLF4J/Logback/Log4j2, bridge JUL to your logging framework:

```xml
<!-- Add dependency -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>jul-to-slf4j</artifactId>
    <version>2.0.9</version>
</dependency>
```

```java
// Install bridge at application startup
import org.slf4j.bridge.SLF4JBridgeHandler;

SLF4JBridgeHandler.removeHandlersForRootLogger();
SLF4JBridgeHandler.install();
```

```xml
<!-- Configure in logback.xml -->
<logger name="com.github.rudygunawan.kachi.Cache" level="WARN"/>
```

**Note:** JUL-to-SLF4J bridge adds ~20% performance overhead (same trade-off as Guava/Caffeine).

#### Example: Debug Mode for Troubleshooting

```java
import java.util.logging.*;

// Enable detailed logging
Logger logger = Logger.getLogger("com.github.rudygunawan.kachi.Cache");
logger.setLevel(Level.FINE);

// Add console handler
ConsoleHandler handler = new ConsoleHandler();
handler.setLevel(Level.FINE);
logger.addHandler(handler);

// Create cache
Cache<String, String> cache = CacheBuilder.newBuilder()
    .maximumSize(10)
    .evictionPolicy(EvictionPolicy.WINDOW_TINY_LFU)
    .build();

// You'll now see logs like:
// [FINE] Evicted entry due to size limit: key=key1, policy=WINDOW_TINY_LFU, size=10
// [FINE] Successfully refreshed entry in background: key=stock123
```

See `LoggingConfigurationExample.java` for complete examples and advanced configuration.

## Configuration Options

| Method | Description | Default |
|--------|-------------|---------|
| `initialCapacity(int)` | Initial capacity of internal hash table | 16 |
| `concurrencyLevel(int)` | Concurrency level for updates | 4 |
| `maximumSize(long)` | Maximum number of entries | unlimited |
| `expireAfterWrite(long, TimeUnit)` | Expire entries after write time | unlimited |
| `expireAfterAccess(long, TimeUnit)` | Expire entries after access time | unlimited |
| `expireAfter(Expiry)` | Custom per-entry expiration logic | none |
| `evictionPolicy(EvictionPolicy)` | Eviction policy (WINDOW_TINY_LFU, LRU, LFU, FIFO) | LRU |
| `refreshAfter(RefreshPolicy)` | Custom refresh policy with time windows | none |
| `refreshAfterWrite(long, TimeUnit)` | Fixed refresh interval | unlimited |
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

## Why Choose Kachi?

### 🎯 **Performance That Clicks** (just like the name!)

- **⚡ Lightning Fast**: 50-100 nanoseconds per operation - as quick as a "kachi" click
- **🏆 Near-Optimal Hit Rates**: Window TinyLFU provides 10-30% better hit rates than LRU
- **🔒 Lock-Free Where It Matters**: Atomic counters and optimistic concurrency
- **🚀 Scales to Millions**: 100-150M concurrent reads/sec on modern hardware

### 💎 **Production-Ready Features**

- **Variable TTL**: Different entries, different lifetimes - premium users get premium cache times
- **Smart Eviction**: Window TinyLFU adapts to both frequency and recency patterns
- **Refresh Ahead**: Background refresh with time-based policies (perfect for stock market data)
- **Scan Resistant**: Large sequential scans don't pollute your hot data
- **Write-Priority**: Readers always get fresh data, never stale

### 🎨 **Developer Experience**

- **Guava-Compatible API**: Drop-in replacement for familiar workflows
- **Zero Dependencies**: No classpath pollution, no version conflicts
- **Type-Safe Builders**: Fluent API with compile-time safety
- **Comprehensive Examples**: Real-world examples (stock markets, user tiers, priority-based)
- **Observable**: Built-in stats + Micrometer integration

### 📚 **Learn While You Code**

- **Educational Codebase**: Clear, well-documented implementation
- **Cache Internals**: See how TTL, LRU, and Window TinyLFU actually work
- **Reference Implementation**: Study Count-Min Sketch, frequency tracking, and admission policies
- **No Magic**: Straightforward concurrent patterns you can understand and trust

### 🆚 **Kachi vs. Alternatives**

| Feature | Kachi | Guava Cache | Caffeine | ConcurrentHashMap |
|---------|-------|-------------|----------|-------------------|
| Window TinyLFU | ✅ | ❌ | ✅ | ❌ |
| Variable TTL | ✅ | ❌ | ✅ | ❌ |
| Refresh Ahead | ✅ | ❌ | ✅ | ❌ |
| Time-Based Refresh | ✅ | ❌ | ❌ | ❌ |
| Zero Dependencies | ✅ | ❌ (Guava) | ❌ (Caffeine) | ✅ |
| Guava-Compatible | ✅ | ✅ | ✅ | ❌ |
| Educational | ✅ | ⚠️ | ⚠️ | ✅ |
| Size | Lightweight | Heavy | Medium | Minimal |

### 🎌 **The Kachi Philosophy**

> _"Like the satisfying 'kachi' sound of a perfectly designed mechanism, your cache operations should snap into place with precision and certainty."_

Kachi isn't just fast—it's **precisely fast**. Every feature is designed to give you that "kachi" moment when things just work:
- ✅ Cache hit? **Kachi.** (instant retrieval)
- ✅ Background refresh? **Kachi.** (old value served, new value loading)
- ✅ Eviction policy? **Kachi.** (hot items stay, cold items go)
- ✅ Thread-safe? **Kachi.** (no race conditions, no deadlocks)

**Victory (勝ち)** in the performance battle, one cache operation at a time.
