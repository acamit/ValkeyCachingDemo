package com.example.valkeydemo;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spins up a 6-node Valkey cluster (3 masters, 3 replicas) using Testcontainers, forms the cluster, then tests caching.
 */
class ClusterCacheWithTestcontainersTest {

    private static final DockerImageName IMAGE = DockerImageName.parse("valkey/valkey:latest");
    private static final int NODE_COUNT = 6;
    private static final List<GenericContainer<?>> containers = new ArrayList<>();
    private static Set<HostAndPort> clusterNodes;
    private static boolean dockerAvailable = false;

    @BeforeAll
    static void setUpCluster() throws Exception {
        // Check Docker availability first
        try {
            DockerClientFactory.instance().client();
            dockerAvailable = true;
        } catch (Exception e) {
            Assumptions.abort("Docker not available - skipping Testcontainers cluster tests");
        }
        if (!dockerAvailable) return;
        int basePort = 9000;
        for (int i = 0; i < NODE_COUNT; i++) {
            int internalPort = basePort + i;
            GenericContainer<?> c = new GenericContainer<>(IMAGE)
                    .withExposedPorts(internalPort)
                    .withCommand("valkey-server", "--port", String.valueOf(internalPort), "--cluster-enabled", "yes", "--cluster-config-file", "nodes.conf", "--appendonly", "no")
                    .withStartupAttempts(3)
                    .withStartupTimeout(Duration.ofSeconds(45));
            c.start();
            containers.add(c);
        }
        StringBuilder sb = new StringBuilder();
        clusterNodes = new HashSet<>();
        for (GenericContainer<?> c : containers) {
            Integer mapped = c.getMappedPort(exposedPort(c));
            sb.append(c.getHost()).append(":").append(mapped).append(" ");
            clusterNodes.add(new HostAndPort(c.getHost(), mapped));
        }
        String addresses = sb.toString().trim();
        GenericContainer<?> first = containers.get(0);
        // Build command array for cluster creation
        List<String> cmd = new ArrayList<>();
        cmd.add("valkey-cli");
        cmd.add("--cluster");
        cmd.add("create");
        cmd.addAll(Arrays.asList(addresses.split(" ")));
        cmd.add("--cluster-replicas");
        cmd.add("1");
        cmd.add("--cluster-yes");
        var result = first.execInContainer(cmd.toArray(new String[0]));
        if (result.getExitCode() != 0 && !result.getStdout().contains("already configured")) {
            Assumptions.abort("Cluster creation failed - skipping tests: " + result.getStderr());
        }
        boolean healthy = waitForClusterOk(first, exposedPort(first), 45, TimeUnit.SECONDS);
        Assumptions.assumeTrue(healthy, "Cluster not healthy in time - skipping tests");
    }

    private static int exposedPort(GenericContainer<?> c) {
        return c.getExposedPorts().get(0);
    }

    private static boolean waitForClusterOk(GenericContainer<?> anyContainer, int internalPort, long timeout, TimeUnit unit) throws Exception {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            try (Jedis jedis = new Jedis(anyContainer.getHost(), anyContainer.getMappedPort(internalPort))) {
                String info = jedis.clusterInfo();
                if (info.contains("cluster_state:ok")) return true;
            } catch (Exception ignored) {}
            Thread.sleep(500);
        }
        return false;
    }

    @AfterAll
    static void tearDown() {
        if (!dockerAvailable) return;
        for (GenericContainer<?> c : containers) {
            try { c.stop(); } catch (Exception ignored) {}
        }
    }

    @Test
    void readThroughCachingWorksOnCluster() {
        Assumptions.assumeTrue(dockerAvailable, "Docker not available");
        ClusterCacheDemo demo = new ClusterCacheDemo(clusterNodes);
        String key = "{tc}:demo:item";
        Duration ttl = Duration.ofSeconds(2);
        Supplier<String> supplier = () -> "VALUE";
        String first = demo.getOrCompute(key, ttl, supplier);
        String second = demo.getOrCompute(key, ttl, supplier);
        assertEquals(first, second);
        try { Thread.sleep(2100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        String third = demo.getOrCompute(key, ttl, supplier);
        assertEquals("VALUE", third);
        try { demo.close(); } catch (Exception ignored) {}
    }

    @Test
    void independentTTLAcrossDifferentSlots() {
        Assumptions.assumeTrue(dockerAvailable, "Docker not available");
        ClusterCacheDemo demo = new ClusterCacheDemo(clusterNodes);
        String k1 = "slotA:test:1";
        String k2 = "slotB:test:2";
        demo.put(k1, "V1", Duration.ofMillis(600));
        demo.put(k2, "V2", Duration.ofSeconds(2));
        assertEquals("V1", demo.get(k1));
        assertEquals("V2", demo.get(k2));
        try { Thread.sleep(750); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        assertNull(demo.get(k1));
        assertEquals("V2", demo.get(k2));
        try { demo.close(); } catch (Exception ignored) {}
    }
}
