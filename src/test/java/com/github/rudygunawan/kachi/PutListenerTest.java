package com.github.rudygunawan.kachi;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.api.CacheStrategy;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.policy.PutCause;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PutListener functionality.
 */
class PutListenerTest {

    /**
     * Test that PutListener is invoked on INSERT (new entry).
     */
    @Test
    void testPutListenerOnInsert() {
        List<String> events = new ArrayList<>();

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .putListener((key, value, cause) -> {
                    events.add("PUT:" + key + "=" + value + ":" + cause);
                })
                .build();

        cache.put("key1", "value1");

        assertEquals(1, events.size());
        assertEquals("PUT:key1=value1:INSERT", events.get(0));
    }

    /**
     * Test that PutListener is invoked on UPDATE (existing entry replacement).
     */
    @Test
    void testPutListenerOnUpdate() {
        List<String> events = new ArrayList<>();

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .putListener((key, value, cause) -> {
                    events.add("PUT:" + key + "=" + value + ":" + cause);
                })
                .build();

        // First put - should be INSERT
        cache.put("key1", "value1");
        assertEquals(1, events.size());
        assertEquals("PUT:key1=value1:INSERT", events.get(0));

        // Second put with same key - should be UPDATE
        cache.put("key1", "value2");
        assertEquals(2, events.size());
        assertEquals("PUT:key1=value2:UPDATE", events.get(1));
    }

    /**
     * Test that PutListener can distinguish between INSERT and UPDATE.
     */
    @Test
    void testPutCauseDistinction() {
        AtomicInteger insertCount = new AtomicInteger(0);
        AtomicInteger updateCount = new AtomicInteger(0);

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .putListener((key, value, cause) -> {
                    if (cause.isNewEntry()) {
                        insertCount.incrementAndGet();
                    } else if (cause.isUpdate()) {
                        updateCount.incrementAndGet();
                    }
                })
                .build();

        // Insert 3 new entries
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        assertEquals(3, insertCount.get());
        assertEquals(0, updateCount.get());

        // Update 2 existing entries
        cache.put("key1", "updated1");
        cache.put("key2", "updated2");

        assertEquals(3, insertCount.get());
        assertEquals(2, updateCount.get());
    }

    /**
     * Test PutListener with HIGH_PERFORMANCE cache strategy.
     */
    @Test
    void testPutListenerWithHighPerformanceStrategy() {
        List<String> events = new ArrayList<>();

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .strategy(CacheStrategy.HIGH_PERFORMANCE)
                .maximumSize(100)
                .putListener((key, value, cause) -> {
                    events.add(key + ":" + cause);
                })
                .build();

        cache.put("key1", "value1");
        cache.put("key1", "value2");
        cache.put("key2", "value3");

        assertEquals(3, events.size());
        assertEquals("key1:INSERT", events.get(0));
        assertEquals("key1:UPDATE", events.get(1));
        assertEquals("key2:INSERT", events.get(2));
    }

    /**
     * Test PutListener with PRECISION cache strategy.
     */
    @Test
    void testPutListenerWithPrecisionStrategy() {
        List<String> events = new ArrayList<>();

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .strategy(CacheStrategy.PRECISION)
                .maximumSize(100)
                .putListener((key, value, cause) -> {
                    events.add(key + ":" + cause);
                })
                .build();

        cache.put("key1", "value1");
        cache.put("key1", "value2");
        cache.put("key2", "value3");

        assertEquals(3, events.size());
        assertEquals("key1:INSERT", events.get(0));
        assertEquals("key1:UPDATE", events.get(1));
        assertEquals("key2:INSERT", events.get(2));
    }

    /**
     * Test that PutListener exceptions are swallowed and don't fail the put operation.
     */
    @Test
    void testPutListenerExceptionHandling() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .putListener((key, value, cause) -> {
                    throw new RuntimeException("Listener exception");
                })
                .build();

        // Put should succeed despite listener exception
        assertDoesNotThrow(() -> cache.put("key1", "value1"));
        assertEquals("value1", cache.getIfPresent("key1"));
    }

    /**
     * Test PutListener with putAll operation.
     */
    @Test
    void testPutListenerWithPutAll() {
        List<String> events = new ArrayList<>();

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .putListener((key, value, cause) -> {
                    events.add(key + ":" + cause);
                })
                .build();

        // Use putAll
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");
        cache.putAll(map);

        // All should be INSERT
        assertEquals(3, events.size());
        assertTrue(events.contains("key1:INSERT"));
        assertTrue(events.contains("key2:INSERT"));
        assertTrue(events.contains("key3:INSERT"));
    }

    /**
     * Test PutListener with concurrent puts.
     */
    @Test
    void testPutListenerConcurrency() throws InterruptedException {
        ConcurrentHashMap<String, PutCause> events = new ConcurrentHashMap<>();

        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .putListener((key, value, cause) -> {
                    events.put(key + ":" + value, cause);
                })
                .build();

        int numThreads = 10;
        int putsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < putsPerThread; j++) {
                        cache.put("thread" + threadId + ":key" + j, j);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify all puts were recorded (may be more than numThreads * putsPerThread due to updates)
        assertTrue(events.size() >= numThreads * putsPerThread);
    }

    /**
     * Test async database upsert pattern (the primary use case).
     */
    @Test
    void testAsyncDatabaseUpsertPattern() throws InterruptedException {
        // Simulate async database operations
        ConcurrentHashMap<String, String> database = new ConcurrentHashMap<>();
        AtomicInteger insertOps = new AtomicInteger(0);
        AtomicInteger updateOps = new AtomicInteger(0);
        ExecutorService dbExecutor = Executors.newFixedThreadPool(4);

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .putListener((key, value, cause) -> {
                    // Async database upsert
                    dbExecutor.submit(() -> {
                        if (cause == PutCause.INSERT) {
                            database.put(key, value);
                            insertOps.incrementAndGet();
                        } else {
                            database.put(key, value);
                            updateOps.incrementAndGet();
                        }
                    });
                })
                .build();

        // Insert new entries
        cache.put("user1", "John Doe");
        cache.put("user2", "Jane Smith");
        cache.put("user3", "Bob Johnson");

        // Update existing entries
        cache.put("user1", "John Updated");
        cache.put("user2", "Jane Updated");

        // Wait for async operations to complete
        dbExecutor.shutdown();
        assertTrue(dbExecutor.awaitTermination(5, TimeUnit.SECONDS));

        // Verify database state
        assertEquals(3, database.size());
        assertEquals("John Updated", database.get("user1"));
        assertEquals("Jane Updated", database.get("user2"));
        assertEquals("Bob Johnson", database.get("user3"));

        // Verify operation counts
        assertEquals(3, insertOps.get());
        assertEquals(2, updateOps.get());
    }

    /**
     * Test that PutListener works with cache eviction.
     */
    @Test
    void testPutListenerWithEviction() {
        List<String> putEvents = new ArrayList<>();

        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(3)
                .putListener((key, value, cause) -> {
                    synchronized (putEvents) {
                        putEvents.add(key + ":" + cause);
                    }
                })
                .build();

        // Fill cache and trigger eviction
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");
        cache.put(4, "four");  // This should trigger eviction

        // All puts should fire events (eviction doesn't affect put events)
        assertTrue(putEvents.size() >= 4);
        assertTrue(putEvents.contains("1:INSERT"));
        assertTrue(putEvents.contains("2:INSERT"));
        assertTrue(putEvents.contains("3:INSERT"));
        assertTrue(putEvents.contains("4:INSERT"));
    }

    /**
     * Test PutListener receives correct key-value pairs.
     */
    @Test
    void testPutListenerReceivesCorrectData() {
        List<String> keys = new ArrayList<>();
        List<Integer> values = new ArrayList<>();

        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .putListener((key, value, cause) -> {
                    keys.add(key);
                    values.add(value);
                })
                .build();

        cache.put("one", 1);
        cache.put("two", 2);
        cache.put("three", 3);

        assertEquals(3, keys.size());
        assertEquals(3, values.size());
        assertTrue(keys.contains("one"));
        assertTrue(keys.contains("two"));
        assertTrue(keys.contains("three"));
        assertTrue(values.contains(1));
        assertTrue(values.contains(2));
        assertTrue(values.contains(3));
    }

    /**
     * Test that null putListener doesn't cause issues.
     */
    @Test
    void testNullPutListener() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .build();

        // Should work fine without a put listener
        assertDoesNotThrow(() -> cache.put("key1", "value1"));
        assertEquals("value1", cache.getIfPresent("key1"));
    }

    /**
     * Test that PutListener is not invoked on get operations.
     */
    @Test
    void testPutListenerNotInvokedOnGet() throws Exception {
        AtomicInteger putCount = new AtomicInteger(0);

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .putListener((key, value, cause) -> {
                    putCount.incrementAndGet();
                })
                .build();

        // Use get with callable - should invoke put listener
        cache.get("key1", () -> "computed");
        assertTrue(putCount.get() > 0);

        int countAfterFirstGet = putCount.get();

        // Use getIfPresent - should NOT invoke put listener
        cache.getIfPresent("key1");
        assertEquals(countAfterFirstGet, putCount.get());
    }
}
