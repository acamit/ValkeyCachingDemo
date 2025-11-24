#!/usr/bin/env bash
set -euo pipefail
# Start single Valkey using docker (if local binary missing)
if ! command -v valkey-server >/dev/null 2>&1; then
  echo "Local valkey-server not found; starting docker container valkey-single"
  docker compose up -d valkey-single
else
  echo "Local valkey-server binary found; starting directly on port 6379"
  valkey-server --port 6379 --save "" --appendonly no &
fi

