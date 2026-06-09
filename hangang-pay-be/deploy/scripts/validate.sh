#!/bin/bash
set -Eeuo pipefail
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo "hangang-pay-be healthy"; exit 0
  fi
  echo "health check $i/30..."; sleep 5
done
echo "health check failed"
if [ -f /opt/hangang-pay-be/docker-compose.yml ]; then
  (cd /opt/hangang-pay-be && docker compose -f docker-compose.yml logs --tail 200 app alloy) || true
else
  docker logs --tail 200 hangang-pay-be || true
fi
exit 1
