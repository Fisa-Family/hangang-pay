#!/bin/bash
set -Eeuo pipefail
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo "hangang-pay-be healthy"; exit 0
  fi
  echo "health check $i/30..."; sleep 5
done
echo "health check failed"; docker logs --tail 200 hangang-pay-be || true
exit 1