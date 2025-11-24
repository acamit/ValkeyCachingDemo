package com.example.valkeydemo;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Demonstrates using Valkey cluster (Redis cluster protocol) for caching.
 * Requires a running Valkey cluster across the provided ports.
 */
public class ClusterCacheDemo {

    private final JedisCluster cluster;

    public ClusterCacheDemo(Set<HostAndPort> nodes) {
        // Use default timeouts and pool settings
        this.cluster = new JedisCluster(nodes);
    }

    public String getOrCompute(String key, Duration ttl, Supplier<String> supplier) {
        String cached = cluster.get(key);
        if (cached != null) return cached;
        String value = supplier.get();
        cluster.setex(key, (int) ttl.toSeconds(), value);
        return value;
    }

    public void put(String key, String value, Duration ttl) {
        cluster.setex(key, (int) ttl.toSeconds(), value);
    }

    public String get(String key) {
        return cluster.get(key);
    }

    public void close() throws IOException { cluster.close(); }

    public static void main(String[] args) throws Exception {
        String baseHost = System.getenv().getOrDefault("VALKEY_CLUSTER_HOST", "127.0.0.1");
        String portsEnv = System.getenv().getOrDefault("VALKEY_CLUSTER_PORTS", "8000,8001,8002,8003,8004,8005");
        java.util.Set<HostAndPort> nodes = new java.util.HashSet<>();
        for (String p : portsEnv.split(",")) {
            nodes.add(new HostAndPort(baseHost, Integer.parseInt(p.trim())));
        }
        ClusterCacheDemo demo = new ClusterCacheDemo(nodes);
        String key = "cluster:demo:item:100";
        Duration ttl = Duration.ofSeconds(10);
        Supplier<String> supplier = () -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) { }
            return "CLUSTER_ITEM_V1";
        };
        long t1 = System.currentTimeMillis();
        System.out.println("First fetch (expect miss)..." + demo.getOrCompute(key, ttl, supplier));
        long missMs = System.currentTimeMillis() - t1;
        long t2 = System.currentTimeMillis();
        System.out.println("Second fetch (expect hit)..." + demo.getOrCompute(key, ttl, supplier));
        long hitMs = System.currentTimeMillis() - t2;
        System.out.printf("Miss latency=%dms, Hit latency=%dms%n", missMs, hitMs);
        demo.close();
    }
}
