package com.example.valkeydemo;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCacheDemoTest {

    static String host;
    static int port;

    @BeforeAll
    static void init() {
        host = System.getenv().getOrDefault("VALKEY_HOST", "127.0.0.1");
        port = Integer.parseInt(System.getenv().getOrDefault("VALKEY_PORT", "6379"));
    }

    @Test
    void cachesValueAndExpires() throws InterruptedException {
        // Connectivity assumption placed here so the test appears as skipped instead of zero tests run.
        try (Socket s = new Socket(host, port)) {
            // reachable
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "Valkey single node not reachable on " + host + ":" + port);
        }
        SimpleCacheDemo demo = new SimpleCacheDemo(host, port);
        AtomicInteger computeCount = new AtomicInteger();
        String key = "junit:test:key";
        Duration ttl = Duration.ofSeconds(1);
        String v1 = demo.getOrCompute(key, ttl, () -> {
            computeCount.incrementAndGet();
            return "VALUE";
        });
        String v2 = demo.getOrCompute(key, ttl, () -> {
            computeCount.incrementAndGet();
            return "VALUE";
        });
        assertEquals(v1, v2);
        assertEquals(1, computeCount.get(), "Should have computed only once before TTL expiry");
        Thread.sleep(1500);
        String v3 = demo.getOrCompute(key, ttl, () -> {
            computeCount.incrementAndGet();
            return "VALUE";
        });
        assertEquals("VALUE", v3);
        assertEquals(2, computeCount.get(), "Should have recomputed after TTL expiry");
    }

    @Test
    void putAndGetRoundTrip() throws InterruptedException {
        try (Socket s = new Socket(host, port)) { } catch (Exception e) {
            Assumptions.assumeTrue(false, "Valkey single node not reachable on " + host + ":" + port);
        }
        SimpleCacheDemo demo = new SimpleCacheDemo(host, port);
        String key = "junit:putget:key";
        demo.put(key, "A", Duration.ofSeconds(1));
        assertEquals("A", demo.get(key));
        Thread.sleep(1200); // wait for expiry
        assertNull(demo.get(key), "Value should have expired");
    }
}
