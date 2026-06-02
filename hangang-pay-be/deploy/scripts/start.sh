#!/bin/bash
set -Eeuo pipefail
REGION=ap-northeast-2
IMAGE=857291404781.dkr.ecr.${REGION}.amazonaws.com/hangang-pay-be:prod-latest
NAME=hangang-pay-be
PREFIX=/hangang-pay/be/prod

# SSM Parameter Store에서 런타임 환경변수 조회
get() { aws ssm get-parameter --name "$PREFIX/$1" --with-decryption --query Parameter.Value --output text --region "$REGION"; }

docker rm -f "$NAME" 2>/dev/null || true

docker run -d \
  --name "$NAME" \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL="$(get DB_URL)" \
  -e DB_USERNAME="$(get DB_USERNAME)" \
  -e DB_PASSWORD="$(get DB_PASSWORD)" \
  -e BANK_BASE_URL="$(get BANK_BASE_URL)" \
  -e REDIS_HOST="$(get REDIS_HOST)" \
  -e REDIS_PORT="$(get REDIS_PORT)" \
  -e VERCEL_PRODUCTION_URL="$(get VERCEL_PRODUCTION_URL)" \
  "$IMAGE"