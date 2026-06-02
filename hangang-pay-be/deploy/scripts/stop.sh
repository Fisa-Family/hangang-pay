#!/bin/bash
set -Eeuo pipefail
# 이전 컨테이너 정리 (green 인스턴스는 보통 없지만 안전하게)
docker rm -f hangang-pay-be 2>/dev/null || true