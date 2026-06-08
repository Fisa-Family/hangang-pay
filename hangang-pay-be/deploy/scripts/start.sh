#!/bin/bash
set -Eeuo pipefail
REGION=ap-northeast-2
ECR=857291404781.dkr.ecr.${REGION}.amazonaws.com
IMAGE=${ECR}/hangang-pay-be:prod-latest
PREFIX=/hangang-pay/be/prod
APP_DIR=/opt/hangang-pay-be

# 1. SSM Parameter Store에서 런타임 환경변수 조회하는 헬퍼
get() { aws ssm get-parameter --name "$PREFIX/$1" --with-decryption --query Parameter.Value --output text --region "$REGION"; }

# 2. 인스턴스 식별자 확보: EC2 IMDSv2 → 실패 시 hostname fallback (instance 라벨용)
imds_token() { curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60" 2>/dev/null || true; }
TOKEN="$(imds_token)"
INSTANCE_ID="$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/instance-id 2>/dev/null || true)"
[ -z "$INSTANCE_ID" ] && INSTANCE_ID="$(hostname)"

# 3. .env 생성 (compose가 app/alloy 양쪽에 주입). 시크릿 보호 위해 권한 600.
umask 077
cat > "$APP_DIR/.env" <<EOF
APP_IMAGE=$IMAGE
SPRING_PROFILES_ACTIVE=prod

# 앱 런타임
DB_URL=$(get DB_URL)
DB_USERNAME=$(get DB_USERNAME)
DB_PASSWORD=$(get DB_PASSWORD)
BANK_BASE_URL=$(get BANK_BASE_URL)
REDIS_HOST=$(get REDIS_HOST)
REDIS_PORT=$(get REDIS_PORT)
VERCEL_PRODUCTION_URL=$(get VERCEL_PRODUCTION_URL)

# Sentry / Grafana Cloud 크리덴셜
SENTRY_DSN=$(get SENTRY_DSN)
SENTRY_RELEASE=$(get SENTRY_RELEASE)
GRAFANA_PROM_URL=$(get GRAFANA_PROM_URL)
GRAFANA_PROM_USER=$(get GRAFANA_PROM_USER)
GRAFANA_PROM_PASSWORD=$(get GRAFANA_PROM_PASSWORD)
GRAFANA_LOKI_URL=$(get GRAFANA_LOKI_URL)
GRAFANA_LOKI_USER=$(get GRAFANA_LOKI_USER)
GRAFANA_LOKI_PASSWORD=$(get GRAFANA_LOKI_PASSWORD)

# Alloy 라벨/타겟 (Blue/Green 색상은 인스턴스가 속한 그룹별 SSM 값)
ALLOY_SERVICE=$(get ALLOY_SERVICE)
DEPLOY_TARGET=$(get DEPLOY_TARGET)
ALLOY_ENV=$(get ALLOY_ENV)
ALLOY_COLOR=$(get ALLOY_COLOR)
APP_SCRAPE_TARGET=$(get APP_SCRAPE_TARGET)
APP_CONTAINER_NAME=app
INSTANCE_ID=$INSTANCE_ID
EOF

# 4. compose 기동 (app + alloy 사이드카)
cd "$APP_DIR"
docker compose -f docker-compose.yml up -d
