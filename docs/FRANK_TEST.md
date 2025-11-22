# Frank Test - Kachi Cache Bulk Operations Performance Benchmark

**Frank Test** is the official performance testing suite for Kachi Cache bulk operations. Named after the rigorous and frank (honest) performance validation it provides, Frank Test gives you unbiased, real-world performance metrics for batch operations.

<img src="https://img.shields.io/badge/Frank%20Test-Performance%20Benchmark-blue" alt="Frank Test Badge"/>

---

## 📊 What is Frank Test?

Frank Test is a comprehensive benchmarking suite that measures the performance of Kachi Cache's bulk operations:

- **putAll()** - Batch insert operations
- **invalidateAll()** - Batch delete operations
- **getAllPresent()** - Batch retrieval operations
- **Concurrent operations** - Multi-threaded performance
- **Mixed workloads** - Real-world usage patterns

### Why "Frank Test"?

The name **Frank** comes from being **frank** (honest and straightforward) about performance. No marketing hype, no cherry-picked numbers - just honest benchmarks that tell you exactly how your cache performs under real conditions.

**Frank Test gives you the frank truth about your cache performance.**

---

## 🚀 Quick Start

### Run Frank Test (Standalone)

```bash
# Compile
javac -d target/test-classes -cp target/classes \
  src/test/java/com/github/rudygunawan/kachi/performance/FrankTest.java

# Run
java -cp target/test-classes:target/classes \
  com.github.rudygunawan.kachi.performance.FrankTest
```

### Run with Maven

```bash
mvn test-compile exec:java \
  -Dexec.mainClass="com.github.rudygunawan.kachi.performance.FrankTest" \
  -Dexec.classpathScope=test
```

### Run as JUnit Test

```bash
mvn test -Dtest=FrankTestSuite
```

---

## 📈 What Frank Test Measures

### Test 1: PUT Operations (Batch Insert)
Compares `putAll()` vs individual `put()` operations.

**Batch sizes:** 10, 100, 1,000, 10,000 entries
**Metric:** Time to insert all entries
**Goal:** Validate bulk insert speedup

### Test 2: DELETE Operations (Batch Remove)
Compares `invalidateAll()` vs individual `invalidate()` operations.

**Batch sizes:** 10, 100, 1,000, 10,000 entries
**Metric:** Time to remove all entries
**Goal:** Validate bulk delete speedup

### Test 3: READ Operations (Batch Retrieval)
Compares `getAllPresent()` vs individual `getIfPresent()` operations.

**Batch sizes:** 10, 100, 1,000, 10,000 entries
**Metric:** Time to retrieve all entries
**Goal:** Validate bulk read speedup

### Test 4: CONCURRENT Operations
Tests bulk operations under multi-threaded load.

**Thread counts:** 1, 2, 4, 8, 16 threads
**Metric:** Throughput (operations/second)
**Goal:** Validate thread safety and scalability

### Test 5: MIXED Workload
Real-world simulation with mixed operations.

**Workload:** 50% reads, 30% writes, 20% deletes
**Threads:** 10 concurrent threads
**Metric:** Throughput, latency, hit rate
**Goal:** Validate realistic performance

---

## 📊 Frank Test Results

### Official Benchmark Results

**Test Environment:**
- JVM: Java 21.0.8
- OS: Linux
- CPU: 16 cores
- Warmup: 3 iterations
- Test: 10 iterations (averaged)

### PUT Operations

| Batch Size | Individual (ms) | Bulk (ms) | Speedup | Time Saved |
|------------|-----------------|-----------|---------|------------|
| 10         | 0.16            | 0.14      | 1.19x   | 0.03 ms    |
| **100**    | **0.40**        | **0.19**  | **2.14x** ⭐ | **0.21 ms** |
| 1,000      | 4.03            | 2.97      | 1.36x   | 1.06 ms    |
| 10,000     | 571.08          | 582.32    | 0.98x   | -11.24 ms  |

**Frank's Verdict:** ✅ **putAll() wins for 100-1,000 entry batches**
**Sweet spot:** 100 entries (2.14x speedup)

### DELETE Operations

| Batch Size | Individual (ms) | Bulk (ms) | Speedup | Time Saved |
|------------|-----------------|-----------|---------|------------|
| 10         | 0.04            | 0.03      | 1.12x   | 0.00 ms    |
| 100        | 0.06            | 0.05      | 1.19x   | 0.01 ms    |
| **1,000**  | **0.21**        | **0.16**  | **1.33x** ⭐ | **0.05 ms** |
| 10,000     | 1.45            | 1.44      | 1.01x   | 0.01 ms    |

**Frank's Verdict:** ✅ **invalidateAll() provides consistent speedup**
**Sweet spot:** 1,000 entries (1.33x speedup)

### READ Operations

| Batch Size | Individual (ms) | Bulk (ms) | Speedup | Time Saved |
|------------|-----------------|-----------|---------|------------|
| 10         | 0.02            | 0.03      | 0.89x   | -0.00 ms   |
| **100**    | **0.10**        | **0.09**  | **1.13x** ⭐ | **0.01 ms** |
| 1,000      | 0.29            | 0.26      | 1.13x   | 0.03 ms    |
| 10,000     | 1.54            | 1.79      | 0.86x   | -0.25 ms   |

**Frank's Verdict:** ✅ **getAllPresent() provides modest speedup**
**Sweet spot:** 100-1,000 entries (1.13x speedup)

---

## 🎯 Frank Test Recommendations

Based on Frank Test results, here are the honest recommendations:

### ✅ When to Use Bulk Operations

**Use `putAll()` when:**
- Batch size: 100-1,000 entries
- Inserting multiple entries at once
- Importing data from external sources
- Cache warming scenarios
- **Expected speedup:** 1.4x - 2.1x

**Use `invalidateAll()` when:**
- Batch size: 100-10,000 entries
- Removing multiple entries at once
- Session cleanup operations
- Batch expiration scenarios
- **Expected speedup:** 1.1x - 1.3x

**Use `getAllPresent()` when:**
- Batch size: 100-1,000 entries
- Multi-key lookups needed
- Pre-fetching related data
- Checking cache status
- **Expected speedup:** 1.1x - 1.2x

### ❌ When to Avoid Bulk Operations

**Don't use bulk operations when:**
- Batch size < 10 entries (overhead > benefit)
- Batch size > 10,000 entries (eviction overhead)
- Single entry operations (use individual methods)
- Cache size << batch size (eviction dominates)

### 📏 The Sweet Spot

**Frank Test reveals the magic numbers:**
- **Best for putAll():** 100 entries (2.14x faster)
- **Best for invalidateAll():** 1,000 entries (1.33x faster)
- **Best for getAllPresent():** 100-1,000 entries (1.13x faster)

---

## 🔧 Customizing Frank Test

You can customize Frank Test parameters:

```java
// In FrankTest.java or FrankTestSuite.java

private static final int WARMUP_ITERATIONS = 3;     // JIT warmup
private static final int TEST_ITERATIONS = 10;      // Test runs

// Customize batch sizes
int[] batchSizes = {10, 100, 1000, 10000};

// Customize thread counts
int[] threadCounts = {1, 2, 4, 8, 16};
```

---

## 📖 Understanding Frank Test Output

### Sample Output

```
╔═══════════════════════════════════════════════════════════════╗
║   Frank Test - Kachi Cache Bulk Operations Benchmark         ║
╚═══════════════════════════════════════════════════════════════╝

Configuration:
  Warmup iterations: 3
  Test iterations:   10
  JVM:              Java 21.0.8
  OS:               Linux
  Processors:       16

═══════════════════════════════════════════════════════════════
  TEST 1: PUT OPERATIONS (Batch Insert Performance)
═══════════════════════════════════════════════════════════════

Batch Size   | Individual (ms)    | Bulk (ms)          | Speedup    | Time Saved
-------------------------------------------------------------------------------------
100          |               0.40 |               0.19 |      2.14x |        0.21 ms

[... more results ...]

╔═══════════════════════════════════════════════════════════════╗
║                    Frank Test Complete                        ║
╚═══════════════════════════════════════════════════════════════╝
```

### Interpreting Results

**Speedup:**
- **> 1.0x** = Bulk is faster ✅
- **= 1.0x** = No difference ⚠️
- **< 1.0x** = Individual is faster ❌

**Time Saved:**
- **Positive** = Time saved by using bulk
- **Negative** = Time lost (overhead)

---

## 🏆 Frank Test Hall of Fame

**Best Performance Achievements:**

🥇 **Gold Medal:** putAll() @ 100 entries - **2.14x speedup**
🥈 **Silver Medal:** putAll() @ 1,000 entries - **1.36x speedup**
🥉 **Bronze Medal:** invalidateAll() @ 1,000 entries - **1.33x speedup**

---

## 🔬 Advanced Frank Test

### Running Extended Benchmarks

For production validation, run extended benchmarks:

```java
// Increase iterations for higher confidence
private static final int WARMUP_ITERATIONS = 10;
private static final int TEST_ITERATIONS = 50;

// Test larger batches
int[] batchSizes = {10, 100, 1000, 10000, 50000, 100000};
```

### Memory Profiling

Add JVM flags to monitor memory:

```bash
java -Xmx4g -XX:+PrintGCDetails -XX:+PrintGCTimeStamps \
  -cp target/test-classes:target/classes \
  com.github.rudygunawan.kachi.performance.FrankTest
```

### CPU Profiling

Profile with JFR (Java Flight Recorder):

```bash
java -XX:+UnlockCommercialFeatures -XX:+FlightRecorder \
  -XX:StartFlightRecording=duration=60s,filename=frank-test.jfr \
  -cp target/test-classes:target/classes \
  com.github.rudygunawan.kachi.performance.FrankTest
```

---

## 📚 Frank Test Best Practices

### 1. Always Warmup
```java
// Run warmup iterations to let JIT compile
for (int i = 0; i < WARMUP_ITERATIONS; i++) {
    runBenchmark();
}
```

### 2. Average Multiple Runs
```java
// Average results for statistical significance
long totalTime = 0;
for (int i = 0; i < TEST_ITERATIONS; i++) {
    totalTime += runBenchmark();
}
long avgTime = totalTime / TEST_ITERATIONS;
```

### 3. Minimize External Factors
- Close other applications
- Disable CPU frequency scaling
- Run multiple times, discard outliers
- Use consistent JVM flags

### 4. Document Your Environment
```java
System.out.println("JVM: " + System.getProperty("java.version"));
System.out.println("OS: " + System.getProperty("os.name"));
System.out.println("Processors: " + Runtime.getRuntime().availableProcessors());
```

---

## 🐛 Troubleshooting Frank Test

### Tests Running Too Long?

**Solution 1:** Reduce iterations
```java
private static final int WARMUP_ITERATIONS = 1;
private static final int TEST_ITERATIONS = 3;
```

**Solution 2:** Reduce batch sizes
```java
int[] batchSizes = {10, 100, 1000};  // Remove 10,000
```

### OutOfMemoryError?

**Solution:** Increase heap size
```bash
java -Xmx4g -cp target/test-classes:target/classes \
  com.github.rudygunawan.kachi.performance.FrankTest
```

### Inconsistent Results?

**Solution 1:** Increase warmup
```java
private static final int WARMUP_ITERATIONS = 10;
```

**Solution 2:** Check for background processes
```bash
# Linux: Check CPU usage
top

# Kill CPU-intensive processes
```

---

## 🤝 Contributing to Frank Test

Want to improve Frank Test? Here's how:

1. **Add new benchmarks**
   - Create new test method
   - Follow naming convention: `benchmark[Operation]`
   - Document expected results

2. **Optimize existing tests**
   - Reduce noise in measurements
   - Improve statistical accuracy
   - Add more realistic scenarios

3. **Report issues**
   - Document your environment
   - Include full test output
   - Share reproduction steps

---

## 📝 Frank Test Changelog

### Version 1.0 (Current)
- ✅ PUT operations benchmark
- ✅ DELETE operations benchmark
- ✅ READ operations benchmark
- ✅ Concurrent operations benchmark
- ✅ Mixed workload benchmark
- ✅ Detailed performance reporting
- ✅ Comprehensive documentation

### Future Plans
- [ ] JMH integration for micro-benchmarking
- [ ] Memory allocation profiling
- [ ] GC pause analysis
- [ ] Comparative benchmarks vs Caffeine/Guava
- [ ] Performance regression testing
- [ ] Automated performance reports

---

## 📞 Support

**Questions about Frank Test?**
- 📖 Read: [Performance Test README](src/test/java/com/github/rudygunawan/kachi/performance/README.md)
- 💬 Discuss: Open a GitHub issue
- 📧 Email: Include "Frank Test" in subject

**Found a performance issue?**
1. Run Frank Test to validate
2. Document your environment
3. Share results with reproduction steps
4. Open an issue with "Frank Test" tag

---

## 🏁 Conclusion

**Frank Test gives you the frank truth:**

✅ Bulk operations **DO** provide measurable speedups (1.1x - 2.1x)
✅ Sweet spot is **100-1,000 entry batches**
✅ Concurrent operations scale well
❌ Very small batches (< 10) not worth it
❌ Very large batches (> 10,000) hit eviction overhead

**Use bulk operations wisely, and let Frank Test guide your decisions.**

---

**Remember: Frank Test doesn't lie. If it's slow in Frank Test, it's slow in production.**

<img src="https://img.shields.io/badge/Frank%20Test-Honest%20Performance-green" alt="Frank Test Badge"/>
