#!/usr/bin/env bash
# 사용법: ./watch_txpool.sh [RPC_URL]
# 기본: http://localhost:8545

RPC="${1:-http://localhost:8545}"

echo "Watching txpool @ $RPC  (Ctrl+C to stop)"
echo "-------------------------------------------"
printf "%-10s %-10s %-10s %-12s %-10s\n" "TIME" "PENDING" "LOCAL" "BLOCK" "TX/BLOCK"

LAST_BN=""
while true; do
  POOL=$(curl -s --max-time 3 -X POST "$RPC" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"txpool_besuStatistics","params":[],"id":1}')

  BLOCK=$(curl -s --max-time 3 -X POST "$RPC" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":2}')

  PENDING=$(echo "$POOL"  | python3 -c "import sys,json; d=json.load(sys.stdin)['result']; print(d['localCount']+d['remoteCount'])" 2>/dev/null || echo "ERR")
  LOCAL=$(echo   "$POOL"  | python3 -c "import sys,json; d=json.load(sys.stdin)['result']; print(d['localCount'])"                  2>/dev/null || echo "ERR")
  BN=$(echo      "$BLOCK" | python3 -c "import sys,json; print(int(json.load(sys.stdin)['result'],16))"                              2>/dev/null || echo "ERR")

  # 새 블록이 생겼을 때만 tx 수 조회
  TX_COUNT="-"
  if [ "$BN" != "$LAST_BN" ] && [ "$BN" != "ERR" ]; then
    HEX=$(python3 -c "print(hex($BN))" 2>/dev/null)
    TX_COUNT=$(curl -s --max-time 3 -X POST "$RPC" \
      -H "Content-Type: application/json" \
      -d "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getBlockTransactionCountByNumber\",\"params\":[\"$HEX\"],\"id\":3}" \
      | python3 -c "import sys,json; print(int(json.load(sys.stdin)['result'],16))" 2>/dev/null || echo "ERR")
    LAST_BN="$BN"
  fi

  printf "%-10s %-10s %-10s %-12s %-10s\n" "$(date +%H:%M:%S)" "$PENDING" "$LOCAL" "$BN" "$TX_COUNT"
  sleep 1
done
