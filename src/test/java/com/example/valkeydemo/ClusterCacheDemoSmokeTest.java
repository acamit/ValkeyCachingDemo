package com.example.valkeydemo;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Smoke test for cluster demo. Skips if cluster ports not available.
 */
class ClusterCacheDemoSmokeTest {

    @Test
    void clusterCachesValue() throws Exception {
        String portsEnv = System.getenv().getOrDefault("VALKEY_CLUSTER_PORTS", "8000,8001,8002,8003,8004,8005");
        String host = System.getenv().getOrDefault("VALKEY_CLUSTER_HOST", "127.0.0.1");
        Set<HostAndPort> nodes = new HashSet<>();
        for (String p : portsEnv.split(",")) {
            nodes.add(new HostAndPort(host, Integer.parseInt(p.trim())));
        }
        // Quick connectivity assumption: only proceed if first node reachable
        try (var socket = new java.net.Socket(host, Integer.parseInt(portsEnv.split(",")[0].trim()))) {
            // reachable
        } catch (Exception e) {
            Assumptions.abort("Cluster not reachable - skipping test");
        }
        ClusterCacheDemo demo = new ClusterCacheDemo(nodes);
        String key = "{slot}:cluster:test"; // {} forces same hash slot for testing
        var ttl = Duration.ofSeconds(2);
        int iterations = 5;
        String last = null;
        for (int i = 0; i < iterations; i++) {
            last = demo.getOrCompute(key, ttl, () -> "VALUE");
        }
        assertEquals("VALUE", last);
        demo.close();
    }

    @Test
    void clusterMultipleKeysWithTTL() throws Exception {
        String portsEnv = System.getenv().getOrDefault("VALKEY_CLUSTER_PORTS", "8000,8001,8002,8003,8004,8005");
        String host = System.getenv().getOrDefault("VALKEY_CLUSTER_HOST", "127.0.0.1");
        Set<HostAndPort> nodes = new HashSet<>();
        for (String p : portsEnv.split(",")) {
            nodes.add(new HostAndPort(host, Integer.parseInt(p.trim())));
        }
        try (var socket = new java.net.Socket(host, Integer.parseInt(portsEnv.split(",")[0].trim()))) {
        } catch (Exception e) {
            Assumptions.abort("Cluster not reachable - skipping test");
        }
        ClusterCacheDemo demo = new ClusterCacheDemo(nodes);
        String key1 = "{grp}:k1"; // slot tag ensures same slot for deterministic testing
        String key2 = "{grp}:k2";
        demo.put(key1, "V1", Duration.ofMillis(500));
        demo.put(key2, "V2", Duration.ofSeconds(2));
        assertEquals("V1", demo.get(key1));
        assertEquals("V2", demo.get(key2));
        Thread.sleep(800); // key1 should expire, key2 still alive
        assertNull(demo.get(key1));
        assertEquals("V2", demo.get(key2));
        Thread.sleep(1500); // now key2 should expire
        assertNull(demo.get(key2));
        demo.close();
    }
}
