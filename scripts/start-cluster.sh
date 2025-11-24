#!/usr/bin/env bash
set -euo pipefail
# Start cluster nodes via docker compose

docker compose up -d valkey-node-8000 valkey-node-8001 valkey-node-8002 valkey-node-8003 valkey-node-8004 valkey-node-8005

echo "Waiting 3s for nodes to be ready..."
sleep 3
./scripts/create-cluster.sh || echo "Cluster creation script finished (may already exist)."

