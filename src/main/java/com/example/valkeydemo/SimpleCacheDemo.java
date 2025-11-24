package com.example.valkeydemo;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Simple caching demo against a single Valkey (Redis-compatible) node.
 * Demonstrates read-through caching pattern with TTL.
 */
public class SimpleCacheDemo {

    private final Jedis jedis;

    public SimpleCacheDemo(String host, int port) {
        this.jedis = new Jedis(host, port);
    }

    /**
     * Get value for key from cache, computing and populating if absent.
     * @param key cache key
     * @param ttl time to live for the cached value
     * @param supplier expensive supplier to compute value if missing
     * @return cached or newly computed value
     */
    public String getOrCompute(String key, Duration ttl, Supplier<String> supplier) {
        String cached = jedis.get(key);
        if (cached != null) {
            return cached;
        }
        // Compute and set with TTL atomically
        String value = supplier.get();
        jedis.set(key, value, SetParams.setParams().ex((int) ttl.toSeconds()));
        return value;
    }

    /**
     * Put a value with TTL.
     */
    public void put(String key, String value, Duration ttl) {
        jedis.set(key, value, SetParams.setParams().ex((int) ttl.toSeconds()));
    }

    /**
     * Get a value (null if absent/expired).
     */
    public String get(String key) {
        return jedis.get(key);
    }

    /**
     * Demonstration main method.
     */
    public static void main(String[] args) throws InterruptedException {
        String host = System.getenv().getOrDefault("VALKEY_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("VALKEY_PORT", "6379"));
        SimpleCacheDemo demo = new SimpleCacheDemo(host, port);
        String key = "demo:product:42";
        Duration ttl = Duration.ofSeconds(5);
        Supplier<String> expensiveCall = () -> {
            // Simulate latency
            try { Thread.sleep(500); } catch (InterruptedException ignored) { }
            return "PRODUCT_DATA_V1"; // pretend this came from a DB/service
        };

        long start = System.currentTimeMillis();
        String first = demo.getOrCompute(key, ttl, expensiveCall);
        long firstMs = System.currentTimeMillis() - start;

        start = System.currentTimeMillis();
        String second = demo.getOrCompute(key, ttl, expensiveCall);
        long secondMs = System.currentTimeMillis() - start;

        System.out.printf("First fetch (miss) value=%s took %dms%n", first, firstMs);
        System.out.printf("Second fetch (hit) value=%s took %dms%n", second, secondMs);

        System.out.println("Waiting for TTL to expire...");
        Thread.sleep(ttl.plusSeconds(1).toMillis());

        start = System.currentTimeMillis();
        String third = demo.getOrCompute(key, ttl, expensiveCall);
        long thirdMs = System.currentTimeMillis() - start;

        System.out.printf("Third fetch (after expiry) value=%s took %dms%n", third, thirdMs);
    }
}
