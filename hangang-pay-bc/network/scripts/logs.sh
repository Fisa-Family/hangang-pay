#!/bin/bash

NETWORK_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$NETWORK_DIR"

# Usage: ./logs.sh [node1|node2|node3|node4]
NODE="$1"

if [ -n "$NODE" ]; then
  docker compose logs -f "$NODE"
else
  docker compose logs -f
fi
