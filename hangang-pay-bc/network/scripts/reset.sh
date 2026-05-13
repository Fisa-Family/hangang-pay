#!/bin/bash
set -e

NETWORK_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$NETWORK_DIR"

echo "Stopping containers..."
docker compose down

echo "Removing chain data..."
for i in 1 2 3 4; do
  rm -rf "node-$i/data/database" \
         "node-$i/data/caches" \
         "node-$i/data/DATABASE_METADATA.json" \
         "node-$i/data/VERSION_METADATA.json"
done

echo "Done. Start with: docker compose up -d"
