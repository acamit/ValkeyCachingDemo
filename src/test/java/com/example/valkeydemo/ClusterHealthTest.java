package com.example.valkeydemo;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.net.Socket;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Valkey cluster reports healthy state (cluster_state:ok) and has expected master count.
 * Skips if the first cluster node is not reachable.
 */
class ClusterHealthTest {

    @Test
    void clusterIsHealthy() throws Exception {
        String portsEnv = System.getenv().getOrDefault("VALKEY_CLUSTER_PORTS", "8000,8001,8002,8003,8004,8005");
        String host = System.getenv().getOrDefault("VALKEY_CLUSTER_HOST", "127.0.0.1");
        List<String> ports = Arrays.asList(portsEnv.split(","));
        int firstPort = Integer.parseInt(ports.get(0).trim());

        // Abort (skip) if cluster not reachable
        try (Socket s = new Socket(host, firstPort)) {
            // reachable
        } catch (Exception e) {
            Assumptions.abort("Cluster not reachable on " + host + ":" + firstPort);
        }

        try (Jedis jedis = new Jedis(host, firstPort)) {
            String info = jedis.clusterInfo();
            assertTrue(info.contains("cluster_state:ok"), "Cluster state should be ok. Info: " + info);
            String nodes = jedis.clusterNodes();
            long masterCount = Arrays.stream(nodes.split("\\n"))
                    .filter(l -> l.contains("master"))
                    .count();
            assertTrue(masterCount >= 3, "Expected >=3 masters, found: " + masterCount + "\nNodes:\n" + nodes);
        }
    }
}
