package com.github.rudygunawan.kachi;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.listener.CacheWriter;
import com.github.rudygunawan.kachi.policy.RemovalCause;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CacheWriter, custom executor, and custom scheduler features.
 */
class CacheWriterAndExecutorTest {

    // ========== CacheWriter Tests ==========

    @Test
    void testCacheWriterOnPut() {
        ConcurrentHashMap<String, String> database = new ConcurrentHashMap<>();
        AtomicInteger writeCount = new AtomicInteger(0);

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .writer(new CacheWriter<String, String>() {
                    @Override
                    public void write(String key, String value) {
                        database.put(key, value);
                        writeCount.incrementAndGet();
                    }

                    @Override
                    public void delete(String key, String value, RemovalCause cause) {
                        // Not deleting on any cause for this test
                    }
                })
                .build();

        // Write to cache
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        // Verify writes to database
        assertEquals(2, writeCount.get());
        assertEquals("value1", database.get("key1"));
        assertEquals("value2", database.get("key2"));
    }

    @Test
    void testCacheWriterOnDelete() {
        ConcurrentHashMap<String, String> database = new ConcurrentHashMap<>();
        AtomicInteger deleteCount = new AtomicInteger(0);

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .writer(new CacheWriter<String, String>() {
                    @Override
                    public void write(String key, String value) {
                        database.put(key, value);
                    }

                    @Override
                    public void delete(String key, String value, RemovalCause cause) {
                        if (cause == RemovalCause.EXPLICIT) {
                            database.remove(key);
                            deleteCount.incrementAndGet();
                        }
                    }
                })
                .build();

        // Write and delete
        cache.put("key1", "value1");
        assertEquals("value1", database.get("key1"));

        cache.invalidate("key1");
        assertEquals(1, deleteCount.get());
        assertNull(database.get("key1"));
    }

    @Test
    void testCacheWriterSyncFactory() {
        ConcurrentHashMap<String, Integer> database = new ConcurrentHashMap<>();

        CacheWriter<String, Integer> writer = CacheWriter.sync(
                (key, value) -> database.put(key, value),
                (key, value, cause) -> {
                    if (cause == RemovalCause.EXPLICIT) {
                        database.remove(key);
                    }
                }
        );

        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .writer(writer)
                .build();

        cache.put("counter", 1);
        assertEquals(1, database.get("counter"));

        cache.invalidate("counter");
        assertNull(database.get("counter"));
    }

    @Test
    void testCacheWriterAsyncFactory() throws Exception {
        ConcurrentHashMap<String, String> database = new ConcurrentHashMap<>();
        ExecutorService writeExecutor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(3);

        CacheWriter<String, String> writer = CacheWriter.async(
                writeExecutor,
                (key, value) -> {
                    database.put(key, value);
                    latch.countDown();
                },
                (key, value, cause) -> {
                    database.remove(key);
                    latch.countDown();
                }
        );

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .writer(writer)
                .build();

        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.invalidate("key1");

        // Wait for async writes
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals("value2", database.get("key2"));
        assertNull(database.get("key1"));

        writeExecutor.shutdown();
    }

    @Test
    void testCacheWriterExceptionHandling() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .writer(new CacheWriter<String, String>() {
                    @Override
                    public void write(String key, String value) {
                        throw new RuntimeException("Write failure");
                    }

                    @Override
                    public void delete(String key, String value, RemovalCause cause) {
                        throw new RuntimeException("Delete failure");
                    }
                })
                .build();

        // Cache operations should succeed despite writer exceptions
        assertDoesNotThrow(() -> cache.put("key1", "value1"));
        assertEquals("value1", cache.getIfPresent("key1"));

        assertDoesNotThrow(() -> cache.invalidate("key1"));
        assertNull(cache.getIfPresent("key1"));
    }

    @Test
    void testCacheWriterWithUpdate() {
        ConcurrentHashMap<String, String> database = new ConcurrentHashMap<>();
        AtomicInteger writeCount = new AtomicInteger(0);

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .writer(CacheWriter.sync(
                        (key, value) -> {
                            database.put(key, value);
                            writeCount.incrementAndGet();
                        },
                        (key, value, cause) -> database.remove(key)
                ))
                .build();

        // Initial write
        cache.put("key1", "value1");
        assertEquals(1, writeCount.get());
        assertEquals("value1", database.get("key1"));

        // Update - should trigger another write
        cache.put("key1", "value2");
        assertEquals(2, writeCount.get());
        assertEquals("value2", database.get("key1"));
    }

    // ========== Custom Executor Tests ==========

    @Test
    void testCustomExecutorConfiguration() {
        Executor customExecutor = Executors.newFixedThreadPool(4);

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .executor(customExecutor)
                .build();

        // Verify executor is set
        assertNotNull(CacheBuilder.newBuilder().executor(customExecutor).getExecutor());
    }

    @Test
    void testExecutorConfigurationValidation() {
        assertThrows(NullPointerException.class, () ->
                CacheBuilder.newBuilder().executor(null)
        );
    }

    // ========== Custom Scheduler Tests ==========

    @Test
    void testCustomSchedulerConfiguration() {
        ScheduledExecutorService customScheduler =
                Executors.newScheduledThreadPool(2);

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(1, TimeUnit.SECONDS)
                .scheduler(customScheduler)
                .build();

        // Verify scheduler is set
        assertNotNull(CacheBuilder.newBuilder()
                .scheduler(customScheduler)
                .getScheduler());

        customScheduler.shutdown();
    }

    @Test
    void testSchedulerConfigurationValidation() {
        assertThrows(NullPointerException.class, () ->
                CacheBuilder.newBuilder().scheduler(null)
        );
    }

    // ========== Integration Tests ==========

    @Test
    void testCacheWriterWithPutListener() {
        ConcurrentHashMap<String, String> database = new ConcurrentHashMap<>();
        AtomicInteger writerCalls = new AtomicInteger(0);
        AtomicInteger listenerCalls = new AtomicInteger(0);

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .writer(CacheWriter.sync(
                        (key, value) -> {
                            database.put(key, value);
                            writerCalls.incrementAndGet();
                        },
                        (key, value, cause) -> database.remove(key)
                ))
                .putListener((key, value, cause) -> listenerCalls.incrementAndGet())
                .build();

        cache.put("key1", "value1");

        // Both writer and listener should be called
        assertEquals(1, writerCalls.get());
        assertEquals(1, listenerCalls.get());
        assertEquals("value1", database.get("key1"));
    }

    @Test
    void testWriteThroughPattern() {
        // Simulates write-through cache pattern
        ConcurrentHashMap<String, String> database = new ConcurrentHashMap<>();

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(10)
                .writer(CacheWriter.sync(
                        (key, value) -> database.put(key, value),
                        (key, value, cause) -> {
                            if (cause == RemovalCause.EXPLICIT) {
                                database.remove(key);
                            }
                            // Don't delete from database on eviction
                        }
                ))
                .build();

        // Write multiple entries
        for (int i = 0; i < 15; i++) {
            cache.put("key" + i, "value" + i);
        }

        // All writes should be in database
        assertEquals(15, database.size());

        // Cache should have evicted some entries
        assertTrue(cache.size() <= 10);

        // Explicit invalidation should remove from both
        cache.invalidate("key14");
        assertFalse(database.containsKey("key14"));
    }

    @Test
    void testWriteBehindPattern() throws Exception {
        // Simulates write-behind cache pattern with batching
        ConcurrentHashMap<String, String> database = new ConcurrentHashMap<>();
        ExecutorService writeExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch latch = new CountDownLatch(10);

        CacheWriter<String, String> writer = CacheWriter.async(
                writeExecutor,
                (key, value) -> {
                    // Simulate slow database write
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    database.put(key, value);
                    latch.countDown();
                },
                (key, value, cause) -> database.remove(key)
        );

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .writer(writer)
                .build();

        // Fast writes to cache
        long start = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            cache.put("key" + i, "value" + i);
        }
        long elapsed = System.nanoTime() - start;

        // Cache writes should be fast (< 100ms)
        assertTrue(elapsed < TimeUnit.MILLISECONDS.toNanos(100));

        // Wait for async writes to complete
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Database should have all entries
        assertEquals(10, database.size());

        writeExecutor.shutdown();
    }
}
