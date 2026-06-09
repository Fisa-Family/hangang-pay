#!/bin/bash
set -Eeuo pipefail
REGION=ap-northeast-2
ECR=857291404781.dkr.ecr.${REGION}.amazonaws.com
IMAGE=${ECR}/hangang-pay-be:prod-latest

# 1. aws cli 설치 (없을 때만)
if ! command -v aws >/dev/null 2>&1; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y unzip curl
  curl -s "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
  unzip -q -o /tmp/awscliv2.zip -d /tmp
  /tmp/aws/install --update
fi

# 2. docker compose 플러그인 확인 (없으면 설치) — app+alloy를 compose로 함께 띄우므로 필수
if ! docker compose version >/dev/null 2>&1; then
  COMPOSE_VERSION=v2.32.4
  mkdir -p /usr/local/lib/docker/cli-plugins
  curl -SL \
    "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
  chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
fi

# 3. Install 단계의 파일 복사가 기존 파일과 충돌하지 않도록 대상 디렉터리 정리
#    (.env는 start.sh가 다시 생성하므로 함께 삭제해도 무방)
rm -rf /opt/hangang-pay-be
mkdir -p /opt/hangang-pay-be

# 4. 인스턴스 IAM 역할 자격으로 ECR 로그인 후 app·alloy 이미지 pull
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ECR"
docker pull "$IMAGE"
docker pull grafana/alloy:latest
