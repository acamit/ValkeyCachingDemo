package com.example.valkeydemo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.redisson.api.RedissonClient;

import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedLockDemoTest {

    static String host;
    static int port;

    @BeforeAll
    static void init() {
        host = System.getenv().getOrDefault("VALKEY_LOCK_HOST", "127.0.0.1");
        port = Integer.parseInt(System.getenv().getOrDefault("VALKEY_LOCK_PORT", "6379"));
    }

    @Test
    void lockSerializesCriticalSection() throws Exception {
        // Connectivity assumption inside test.
        try (Socket s = new Socket(host, port)) {
            // reachable
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "Valkey single node not reachable on " + host + ":" + port);
        }
        RedissonClient client = DistributedLockDemo.createSingleClient(host, port);
        DistributedLockDemo demo = new DistributedLockDemo(client);
        AtomicInteger counter = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(2);
        Runnable critical = () -> {
            int before = counter.get();
            try { Thread.sleep(200); } catch (InterruptedException ignored) { }
            counter.set(before + 1);
            latch.countDown();
        };
        Thread t1 = new Thread(() -> {
            try { demo.runWithLock("junit:lock", critical, Duration.ofSeconds(2)); } catch (InterruptedException ignored) { }
        });
        Thread t2 = new Thread(() -> {
            try { demo.runWithLock("junit:lock", critical, Duration.ofSeconds(2)); } catch (InterruptedException ignored) { }
        });
        t1.start();
        t2.start();
        latch.await();
        assertEquals(2, counter.get(), "Critical section should have executed twice serially");
        client.shutdown();
    }

    @Test
    void tryAcquireShowsContention() throws Exception {
        try (Socket s = new Socket(host, port)) { } catch (Exception e) {
            Assumptions.assumeTrue(false, "Valkey single node not reachable on " + host + ":" + port);
        }
        RedissonClient client = DistributedLockDemo.createSingleClient(host, port);
        DistributedLockDemo demo = new DistributedLockDemo(client);
        String lock = "junit:lock:contention";
        // Acquire first in main test thread
        assertTrue(demo.tryAcquire(lock, Duration.ofSeconds(2)), "First acquisition should succeed");
        AtomicBoolean secondAcquired = new AtomicBoolean(true); // default true so we notice if not set
        Thread competitor = new Thread(() -> secondAcquired.set(demo.tryAcquire(lock, Duration.ofSeconds(2))));
        competitor.start();
        competitor.join();
        assertFalse(secondAcquired.get(), "Second acquisition from different thread should fail due to contention");
        demo.release(lock);
        AtomicBoolean thirdAcquired = new AtomicBoolean(false);
        Thread competitor2 = new Thread(() -> thirdAcquired.set(demo.tryAcquire(lock, Duration.ofSeconds(2))));
        competitor2.start();
        competitor2.join();
        assertTrue(thirdAcquired.get(), "Acquisition after release by different thread should succeed");
        demo.release(lock);
        client.shutdown();
    }
}
