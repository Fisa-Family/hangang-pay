#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_DIR="$SCRIPT_DIR/../artifacts/contracts"
TARGET_DIR="$SCRIPT_DIR/../../../hangang-pay-be/src/main/resources/contracts"

if [ ! -d "$ARTIFACTS_DIR" ]; then
  echo "Error: artifacts not found. Run 'npx hardhat compile' first."
  exit 1
fi

mkdir -p "$TARGET_DIR"

find "$ARTIFACTS_DIR" -maxdepth 2 -name "*.json" ! -name "*.dbg.json" | while read -r src; do
  filename="$(basename "$src")"
  cp "$src" "$TARGET_DIR/$filename"
  echo "Copied: $filename → $TARGET_DIR/"
done

echo "Done."
