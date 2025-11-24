#!/usr/bin/env bash
set -euo pipefail
# Creates Valkey cluster across nodes 8000-8005 (must already be running via docker-compose up -d)
NODES=(8000 8001 8002 8003 8004 8005)
ADDRESSES=""
for p in "${NODES[@]}"; do
  ADDRESSES+="127.0.0.1:${p} "
done
ADDRESSES=$(echo "$ADDRESSES" | xargs) # trim

echo "Creating cluster with addresses: $ADDRESSES"
docker exec -it valkey-node-8000 valkey-cli --cluster create $ADDRESSES --cluster-replicas 1

