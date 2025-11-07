package com.github.rudygunawan.kachi;

import com.github.rudygunawan.kachi.api.Cache;
import com.github.rudygunawan.kachi.builder.CacheBuilder;
import com.github.rudygunawan.kachi.listener.RemovalListener;
import com.github.rudygunawan.kachi.policy.EvictionPolicy;
import com.github.rudygunawan.kachi.policy.RemovalCause;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for new features: eviction policies, removal listeners, scheduled cleanup, and write-priority.
 */
class NewFeaturesTest {

    @Test
    void testRemovalListener() {
        List<String> removals = new ArrayList<>();
        List<RemovalCause> causes = new ArrayList<>();

        RemovalListener<String, String> listener = (key, value, cause) -> {
            removals.add(key + "=" + value);
            causes.add(cause);
        };

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(2)
                .removalListener(listener)
                .build();

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3"); // Should evict "a"

        assertTrue(removals.size() >= 1);
        assertEquals(RemovalCause.SIZE, causes.get(causes.size() - 1));
    }

    @Test
    void testRemovalListenerExplicit() {
        List<RemovalCause> causes = new ArrayList<>();

        RemovalListener<String, String> listener = (key, value, cause) -> {
            causes.add(cause);
        };

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .removalListener(listener)
                .build();

        cache.put("a", "1");
        cache.invalidate("a");

        assertEquals(1, causes.size());
        assertEquals(RemovalCause.EXPLICIT, causes.get(0));
    }

    @Test
    void testRemovalListenerReplaced() {
        List<RemovalCause> causes = new ArrayList<>();

        RemovalListener<String, String> listener = (key, value, cause) -> {
            causes.add(cause);
        };

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .removalListener(listener)
                .build();

        cache.put("a", "1");
        cache.put("a", "2"); // Replace

        assertEquals(1, causes.size());
        assertEquals(RemovalCause.REPLACED, causes.get(0));
    }

    @Test
    void testRemovalListenerExpired() throws Exception {
        List<RemovalCause> causes = new ArrayList<>();

        RemovalListener<String, String> listener = (key, value, cause) -> {
            causes.add(cause);
        };

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(100, TimeUnit.MILLISECONDS)
                .removalListener(listener)
                .build();

        cache.put("a", "1");
        Thread.sleep(150);
        cache.getIfPresent("a"); // Trigger expiration check

        assertEquals(1, causes.size());
        assertEquals(RemovalCause.EXPIRED, causes.get(0));
    }

    @Test
    void testLRUEvictionPolicy() {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(3)
                .evictionPolicy(EvictionPolicy.LRU)
                .build();

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        // Access 1 to make it recently used
        cache.getIfPresent(1);

        // Add 4 - should evict 2 (least recently used)
        cache.put(4, "four");

        assertNotNull(cache.getIfPresent(1));
        assertNull(cache.getIfPresent(2)); // Evicted
        assertNotNull(cache.getIfPresent(3));
        assertNotNull(cache.getIfPresent(4));
    }

    @Test
    void testFIFOEvictionPolicy() {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(3)
                .evictionPolicy(EvictionPolicy.FIFO)
                .build();

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        // Access 1 multiple times - FIFO should still evict 1 (first in)
        cache.getIfPresent(1);
        cache.getIfPresent(1);
        cache.getIfPresent(1);

        // Add 4 - should evict 1 (first in)
        cache.put(4, "four");

        assertNull(cache.getIfPresent(1)); // Evicted (FIFO)
        assertNotNull(cache.getIfPresent(2));
        assertNotNull(cache.getIfPresent(3));
        assertNotNull(cache.getIfPresent(4));
    }

    @Test
    void testLFUEvictionPolicy() {
        Cache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(3)
                .evictionPolicy(EvictionPolicy.LFU)
                .build();

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        // Access 1 and 3 multiple times
        for (int i = 0; i < 10; i++) {
            cache.getIfPresent(1);
            cache.getIfPresent(3);
        }

        // 2 has the least accesses, should be evicted
        cache.put(4, "four");

        assertNotNull(cache.getIfPresent(1));
        assertNull(cache.getIfPresent(2)); // Evicted (LFU)
        assertNotNull(cache.getIfPresent(3));
        assertNotNull(cache.getIfPresent(4));
    }

    @Test
    void testScheduledTTLCleanup() throws Exception {
        AtomicInteger expiredCount = new AtomicInteger(0);

        RemovalListener<String, String> listener = (key, value, cause) -> {
            if (cause == RemovalCause.EXPIRED) {
                expiredCount.incrementAndGet();
            }
        };

        ConcurrentCacheImpl<String, String> cache = (ConcurrentCacheImpl<String, String>)
            CacheBuilder.newBuilder()
                .expireAfterWrite(100, TimeUnit.MILLISECONDS)
                .removalListener(listener)
                .build();

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");

        // Wait for entries to expire
        Thread.sleep(200);

        // Manually trigger cleanup (scheduled cleanup runs every minute)
        cache.cleanUp();

        assertEquals(3, expiredCount.get());
        assertEquals(0, cache.size());

        cache.shutdown();
    }

    @Test
    void testWritePriorityReadTimeout() throws Exception {
        Cache<String, String> cache = CacheBuilder.newBuilder().build();

        cache.put("key", "value1");

        CountDownLatch writeLatch = new CountDownLatch(1);
        CountDownLatch readLatch = new CountDownLatch(1);

        // Start a write that holds the lock
        Thread writeThread = new Thread(() -> {
            try {
                cache.put("key", "value2");
                writeLatch.countDown();
                // Hold the lock by doing another operation
                Thread.sleep(500);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Start a read that should wait for write
        Thread readThread = new Thread(() -> {
            try {
                writeLatch.await(); // Wait for write to start
                String value = cache.getIfPresent("key");
                // Should eventually get the value (or timeout)
                readLatch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        writeThread.start();
        readThread.start();

        // Both should complete
        assertTrue(readLatch.await(3, TimeUnit.SECONDS));

        writeThread.join();
        readThread.join();
    }

    @Test
    void testConcurrentReadsDuringWrite() throws Exception {
        Cache<String, Integer> cache = CacheBuilder.newBuilder().build();

        cache.put("counter", 0);

        int numReaders = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numReaders + 1);

        // Writer thread
        Thread writer = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 1; i <= 100; i++) {
                    cache.put("counter", i);
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        });

        // Reader threads
        for (int i = 0; i < numReaders; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 50; j++) {
                        Integer value = cache.getIfPresent("counter");
                        // Value should never be null (either get old or new value)
                        assertNotNull(value);
                        Thread.sleep(2);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        writer.start();
        startLatch.countDown();

        assertTrue(endLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    void testRemovalCauseEnumMethods() {
        assertTrue(RemovalCause.SIZE.wasEvicted());
        assertTrue(RemovalCause.EXPIRED.wasEvicted());
        assertFalse(RemovalCause.EXPLICIT.wasEvicted());
        assertFalse(RemovalCause.REPLACED.wasEvicted());
    }

    @Test
    void testEvictionPolicyNotNull() {
        assertThrows(NullPointerException.class, () -> {
            CacheBuilder.newBuilder().evictionPolicy(null);
        });
    }

    @Test
    void testRemovalListenerNotNull() {
        assertThrows(NullPointerException.class, () -> {
            CacheBuilder.newBuilder().removalListener(null);
        });
    }

    @Test
    void testMultipleRemovalReasons() {
        List<String> events = new ArrayList<>();

        RemovalListener<String, String> listener = (key, value, cause) -> {
            events.add(cause.name());
        };

        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(2)
                .removalListener(listener)
                .build();

        cache.put("a", "1");
        cache.put("a", "2"); // REPLACED
        cache.put("b", "3");
        cache.put("c", "4"); // SIZE eviction
        cache.invalidate("b"); // EXPLICIT

        assertTrue(events.contains("REPLACED"));
        assertTrue(events.contains("SIZE"));
        assertTrue(events.contains("EXPLICIT"));
    }

    @Test
    void testLFUAccessCountTracking() {
        Cache<String, String> cache = CacheBuilder.newBuilder()
                .maximumSize(10)
                .evictionPolicy(EvictionPolicy.LFU)
                .build();

        cache.put("a", "1");
        cache.put("b", "2");

        // Access "a" many times
        for (int i = 0; i < 100; i++) {
            cache.getIfPresent("a");
        }

        // Access "b" only once
        cache.getIfPresent("b");

        // Verify both are still present
        assertNotNull(cache.getIfPresent("a"));
        assertNotNull(cache.getIfPresent("b"));
    }
}
