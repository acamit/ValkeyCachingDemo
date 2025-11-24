#!/usr/bin/env bash
set -euo pipefail
# Starts valkey cluster (if not up), creates it if needed, then runs cluster-related tests.
PROJECT_ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$PROJECT_ROOT"

echo "[cluster] Bringing up cluster containers..."
docker compose up -d valkey-node-8000 valkey-node-8001 valkey-node-8002 valkey-node-8003 valkey-node-8004 valkey-node-8005 >/dev/null

bash scripts/create-cluster.sh || true

echo "[cluster] Waiting briefly for cluster to stabilize..."
sleep 2

echo "[cluster] Cluster info:"
docker exec valkey-node-8000 valkey-cli cluster info | grep -E 'cluster_state|cluster_size'

echo "[cluster] Running cluster tests..."
VALKEY_CLUSTER_HOST=127.0.0.1 VALKEY_CLUSTER_PORTS=8000,8001,8002,8003,8004,8005 mvn -q -Dtest='Cluster*Test' test || {
  echo "Cluster tests failed" >&2
  exit 1
}

echo "[cluster] Running health test..."
VALKEY_CLUSTER_HOST=127.0.0.1 VALKEY_CLUSTER_PORTS=8000,8001,8002,8003,8004,8005 mvn -q -Dtest='ClusterHealthTest' test || {
  echo "Cluster health test failed" >&2
  exit 1
}

echo "[cluster] All cluster tests passed."
