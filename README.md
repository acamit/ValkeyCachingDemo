Demo to test reddision and valkey caching.

# Valkey Caching Demo (Java)

This project provides three Java demonstrations using Valkey (Redis protocol compatible):

1. Simple caching (read-through with TTL) using Jedis
2. Cluster caching using JedisCluster (ports 8000-8005 as provided in `valkey-test/cluster-test`)
3. Distributed locks using Redisson (single node or cluster)

## Prerequisites

- Java 17+
- Maven 3.8+
- A running Valkey server (single node) on 6379 for simple + lock demo (or set env vars)
- A running Valkey cluster across ports 8000-8005 for cluster demo

## Starting Valkey Single Node (Example)

```bash
valkey-server --port 6379 --save "" --appendonly no
```

Export environment variables if you use a different host/port:

```bash
export VALKEY_HOST=127.0.0.1
export VALKEY_PORT=6379
```

## Starting Valkey Cluster (Using provided configs)

In one shell per node (or background them):

```bash
valkey-server valkey-test/cluster-test/8000/valkey.conf &
valkey-server valkey-test/cluster-test/8001/valkey.conf &
valkey-server valkey-test/cluster-test/8002/valkey.conf &
valkey-server valkey-test/cluster-test/8003/valkey.conf &
valkey-server valkey-test/cluster-test/8004/valkey.conf &
valkey-server valkey-test/cluster-test/8005/valkey.conf &
```

If not already clustered, create the cluster (only once):

```bash
valkey-cli --cluster create 127.0.0.1:8000 127.0.0.1:8001 127.0.0.1:8002 127.0.0.1:8003 127.0.0.1:8004 127.0.0.1:8005 --cluster-replicas 1
```

Set env vars if needed:

```bash
export VALKEY_CLUSTER_HOST=127.0.0.1
export VALKEY_CLUSTER_PORTS=8000,8001,8002,8003,8004,8005
```

## Build

```bash
mvn -q clean package
```

## Run Simple Cache Demo

```bash
java -cp target/valkey-caching-demo-0.1.0-SNAPSHOT.jar com.example.valkeydemo.SimpleCacheDemo
```

## Run Cluster Cache Demo

```bash
java -cp target/valkey-caching-demo-0.1.0-SNAPSHOT.jar com.example.valkeydemo.ClusterCacheDemo
```

## Run Distributed Lock Demo

```bash
java -cp target/valkey-caching-demo-0.1.0-SNAPSHOT.jar com.example.valkeydemo.DistributedLockDemo
```

To use cluster for locks, set:

```bash
export VALKEY_LOCK_HOST=127.0.0.1
export VALKEY_LOCK_PORT=6379
```
(or adapt the code to use createClusterClient with ports.)

## Tests

Run unit tests (requires single node Valkey running):

```bash
mvn -q test
```

## Extending

- Add serialization (e.g., JSON) for complex objects
- Add metrics around cache hits/misses
- Implement cache stampede protection (e.g., mutex key or Redisson lock)
- Add Lua scripts for atomic multi-key operations

## Disclaimer

Versions pinned as of late 2025; update if newer stable releases are available.

## Docker Quick Start (Alternative)

If local `valkey-server` binary is not available, use Docker:

```bash
# Start single node
./scripts/start-single.sh

# Run tests (single-node dependent tests will execute now)
mvn test -Dtest=SimpleCacheDemoTest,DistributedLockDemoTest

# Start cluster nodes
./scripts/start-cluster.sh

# Run cluster smoke test
mvn test -Dtest=ClusterCacheDemoSmokeTest
```

To tear down containers:

```bash
docker compose down
```

## Running Cluster Tests & Health Check

After starting the cluster (via manual valkey-server processes or `./scripts/start-cluster.sh`):

```bash
export VALKEY_CLUSTER_HOST=127.0.0.1
export VALKEY_CLUSTER_PORTS=8000,8001,8002,8003,8004,8005
mvn -q -Dtest='Cluster*Test' test
```

If tests are skipped, verify cluster state:

```bash
docker exec valkey-node-8000 valkey-cli cluster info | grep cluster_state
docker exec valkey-node-8000 valkey-cli cluster nodes | head
```

Run dedicated health test:

```bash
mvn -q -Dtest=ClusterHealthTest test
```

Alternatively, use the helper script (Docker setup):

```bash
bash scripts/test-cluster.sh
```

### Troubleshooting
- Skipped tests: First node not reachable; ensure ports are published and containers running: `docker ps | grep valkey-node-8000`
- Cluster state not ok: Re-run `scripts/create-cluster.sh` or manual `valkey-cli --cluster create` command.
- Connection refused: Wait a few seconds after container start; confirm no firewall blocking 8000-8005.
- Master count < 3: Ensure `--cluster-replicas 1` was used and all 6 nodes were in the creation command.
