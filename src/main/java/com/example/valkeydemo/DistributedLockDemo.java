package com.example.valkeydemo;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates distributed locking with Redisson on Valkey.
 */
public class DistributedLockDemo {

    private final RedissonClient redisson;

    public DistributedLockDemo(RedissonClient client) {
        this.redisson = client;
    }

    public static RedissonClient createSingleClient(String host, int port) {
        Config config = new Config();
        config.useSingleServer().setAddress(String.format("redis://%s:%d", host, port));
        return Redisson.create(config);
    }

    public static RedissonClient createClusterClient(String host, String[] ports) {
        Config config = new Config();
        var clusterServers = config.useClusterServers();
        for (String p : ports) {
            clusterServers.addNodeAddress(String.format("redis://%s:%s", host, p));
        }
        return Redisson.create(config);
    }

    /**
     * Acquire a lock, run critical section, then release.
     */
    public void runWithLock(String lockName, Runnable criticalSection, Duration leaseTime) throws InterruptedException {
        RLock lock = redisson.getLock(lockName);
        // Wait up to 5 seconds to acquire; lease time in same unit (milliseconds)
        boolean acquired = lock.tryLock(5000, leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        if (!acquired) {
            throw new IllegalStateException("Could not acquire lock: " + lockName);
        }
        try {
            criticalSection.run();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Try to acquire a lock non-blocking, returning true if acquired.
     */
    public boolean tryAcquire(String lockName, Duration leaseTime) {
        RLock lock = redisson.getLock(lockName);
        try {
            return lock.tryLock(0, leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Release lock if held by current thread.
     */
    public void release(String lockName) {
        RLock lock = redisson.getLock(lockName);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public void shutdown() { redisson.shutdown(); }

    public static void main(String[] args) throws Exception {
        String host = System.getenv().getOrDefault("VALKEY_LOCK_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("VALKEY_LOCK_PORT", "6379"));
        RedissonClient client = createSingleClient(host, port);
        try {
            DistributedLockDemo demo = new DistributedLockDemo(client);
            demo.runWithLock("lock:demo", () -> {
                System.out.println("Critical section executing...");
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { }
                System.out.println("Critical section complete.");
            }, Duration.ofSeconds(5));
        } finally {
            client.shutdown();
        }
    }
}
