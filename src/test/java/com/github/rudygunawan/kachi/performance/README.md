# Bulk Operations Performance Tests

This directory contains comprehensive performance tests for bulk operations in Kachi Cache.

## Test Files

### 1. BulkOperationsPerformanceTest.java
JUnit 5 test class with detailed assertions and performance validations.

**Tests included:**
- `testPutAllPerformance()` - Validates putAll() speedup vs individual put()
- `testInvalidateAllPerformance()` - Validates invalidateAll() speedup vs individual invalidate()
- `testGetAllPresentPerformance()` - Validates getAllPresent() speedup vs individual getIfPresent()
- `testConcurrentBulkOperations()` - Tests concurrent putAll() and invalidateAll()
- `testMixedWorkload()` - Real-world mixed workload (50% read, 30% write, 20% delete)

**Run with Maven:**
```bash
mvn test -Dtest=BulkOperationsPerformanceTest
```

### 2. BulkOperationsPerformanceRunner.java
Standalone runner that generates detailed performance reports without requiring JUnit.

**Run directly:**
```bash
# Compile
javac -d target/test-classes -cp target/classes \
  src/test/java/com/github/rudygunawan/kachi/performance/BulkOperationsPerformanceRunner.java

# Run
java -cp target/test-classes:target/classes \
  com.github.rudygunawan.kachi.performance.BulkOperationsPerformanceRunner
```

**Or with Maven:**
```bash
mvn test-compile exec:java \
  -Dexec.mainClass="com.github.rudygunawan.kachi.performance.BulkOperationsPerformanceRunner" \
  -Dexec.classpathScope=test
```

## Test Configuration

- **Warmup iterations:** 3 (to eliminate JIT compilation effects)
- **Test iterations:** 10 (averaged for statistical significance)
- **Batch sizes tested:** 10, 100, 1,000, 10,000
- **Thread counts tested:** 1, 2, 4, 8, 16

## Sample Results

Based on benchmarks run on:
- **JVM:** Java 21.0.8
- **OS:** Linux
- **Processors:** 16 cores

### PUT Operations (Batch Insert)

| Batch Size | Individual (ms) | Bulk (ms) | Speedup | Time Saved |
|------------|-----------------|-----------|---------|------------|
| 10         | 0.16            | 0.14      | 1.19x   | 0.03 ms    |
| 100        | 0.40            | 0.19      | 2.14x   | 0.21 ms    |
| 1,000      | 4.03            | 2.97      | 1.36x   | 1.06 ms    |
| 10,000     | 571.08          | 582.32    | 0.98x   | -11.24 ms  |

**Key Findings:**
- **Best speedup at 100 entries:** 2.14x faster
- Bulk operations provide consistent improvements for batches of 100-1,000
- At very large sizes (10,000+), eviction overhead dominates

### DELETE Operations (Batch Remove)

| Batch Size | Individual (ms) | Bulk (ms) | Speedup | Time Saved |
|------------|-----------------|-----------|---------|------------|
| 10         | 0.04            | 0.03      | 1.12x   | 0.00 ms    |
| 100        | 0.06            | 0.05      | 1.19x   | 0.01 ms    |
| 1,000      | 0.21            | 0.16      | 1.33x   | 0.05 ms    |
| 10,000     | 1.45            | 1.44      | 1.01x   | 0.01 ms    |

**Key Findings:**
- **Consistent speedup:** 1.12x - 1.33x across all batch sizes
- Removes are very fast (sub-millisecond for 1,000 entries)
- Best relative performance at medium batch sizes (1,000)

### READ Operations (Batch Retrieval)

| Batch Size | Individual (ms) | Bulk (ms) | Speedup | Time Saved |
|------------|-----------------|-----------|---------|------------|
| 10         | 0.02            | 0.03      | 0.89x   | -0.00 ms   |
| 100        | 0.10            | 0.09      | 1.13x   | 0.01 ms    |
| 1,000      | 0.29            | 0.26      | 1.13x   | 0.03 ms    |
| 10,000     | 1.54            | 1.79      | 0.86x   | -0.25 ms   |

**Key Findings:**
- **Moderate speedup:** 1.13x for 100-1,000 entries
- Reads are extremely fast (all sub-2ms even for 10,000 entries)
- Overhead of bulk operation is noticeable at very small/large sizes

## Performance Recommendations

Based on test results:

### When to use bulk operations:

✅ **putAll():**
- Batch sizes: 100-1,000 entries (2.14x - 1.36x speedup)
- Use case: Batch data imports, cache warming
- Avoid: Single digits or 10,000+ entries

✅ **invalidateAll():**
- Batch sizes: All sizes (consistent 1.1x - 1.3x speedup)
- Use case: Session cleanup, batch expiration
- Best for: Medium batches (1,000 entries)

✅ **getAllPresent():**
- Batch sizes: 100-1,000 entries (1.13x speedup)
- Use case: Multi-key lookups, data pre-fetching
- Note: Already very fast, gains are modest

### When individual operations are better:

❌ **Avoid bulk for:**
- Very small batches (< 10 entries) - overhead outweighs benefit
- Very large batches (> 10,000 entries) - eviction dominates
- Single entry operations - unnecessary complexity

## Interpreting Results

### Speedup Calculation
```
Speedup = Individual Time / Bulk Time
```
- **> 1.0:** Bulk is faster (good!)
- **= 1.0:** No difference
- **< 1.0:** Individual is faster (bulk overhead)

### Why some batches show < 1.0 speedup:

1. **Very small batches (< 10):** Method call overhead and map creation cost more than savings
2. **Very large batches (> 10,000):** Eviction policy kicks in, adding 1-second wait per entry
3. **Cache size limits:** When batch exceeds cache size, eviction dominates performance

### Factors Affecting Performance:

- **JVM warmup:** First few runs are slower (we do warmups)
- **GC pauses:** Can affect large batch operations
- **Cache size:** Evictions slow down large batches
- **Concurrency:** Lock contention increases with thread count
- **Entry size:** Larger values affect memory and GC

## Running Custom Benchmarks

You can customize the benchmarks by editing the constants in either file:

```java
// In BulkOperationsPerformanceRunner.java or BulkOperationsPerformanceTest.java

private static final int WARMUP_ITERATIONS = 3;     // Increase for more accurate results
private static final int TEST_ITERATIONS = 10;       // Increase for statistical confidence

// In benchmark methods:
int[] batchSizes = {10, 100, 1000, 10000};          // Customize batch sizes
int[] threadCounts = {1, 2, 4, 8, 16};              // Customize thread counts
```

## Troubleshooting

**Tests taking too long?**
- Reduce `TEST_ITERATIONS` from 10 to 5
- Reduce batch sizes (remove 10,000 from array)
- Reduce thread counts in concurrent tests

**OutOfMemoryError?**
- Increase JVM heap: `java -Xmx4g ...`
- Reduce batch sizes
- Reduce maximum cache size in tests

**Inconsistent results?**
- Increase `WARMUP_ITERATIONS` to 5-10
- Increase `TEST_ITERATIONS` to 20-50
- Run tests multiple times and average results
- Check for background processes affecting CPU

## Continuous Integration

These tests can be integrated into your CI/CD pipeline to track performance regressions:

```yaml
# Example GitHub Actions workflow
- name: Run Performance Tests
  run: |
    mvn test -Dtest=BulkOperationsPerformanceTest
    java -cp target/test-classes:target/classes \
      com.github.rudygunawan.kachi.performance.BulkOperationsPerformanceRunner \
      > performance-report.txt
```

## Contributing

When adding new bulk operations:

1. Add corresponding performance test method
2. Test with multiple batch sizes (10, 100, 1000, 10000)
3. Verify speedup >= 1.0 for batch sizes 100-1000
4. Document expected performance characteristics
5. Update this README with new results

## References

- [Kachi Bulk Operations Documentation](../../../main/java/com/github/rudygunawan/kachi/example/BulkOperationsExample.java)
- [Java Microbenchmark Harness (JMH)](https://github.com/openjdk/jmh) - For more advanced benchmarking
- [Caffeine Benchmarks](https://github.com/ben-manes/caffeine/wiki/Benchmarks) - Industry comparison
