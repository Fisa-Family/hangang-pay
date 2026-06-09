#!/bin/bash
set -Eeuo pipefail
APP_DIR=/opt/hangang-pay-be

# 1. compose로 기동된 app+alloy 정리. compose 파일이 있으면 그것으로 down.
if [ -f "$APP_DIR/docker-compose.yml" ]; then
  cd "$APP_DIR"
  docker compose -f docker-compose.yml down || true
fi

# 2. 과거 docker run 컨테이너 및 잔여 컨테이너 안전 정리 (compose 전환 첫 배포 대비)
docker rm -f app alloy hangang-pay-be 2>/dev/null || true
