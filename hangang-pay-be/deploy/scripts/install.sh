#!/bin/bash
set -Eeuo pipefail
REGION=ap-northeast-2
ECR=857291404781.dkr.ecr.${REGION}.amazonaws.com
IMAGE=${ECR}/hangang-pay-be:prod-latest

if ! command -v aws >/dev/null 2>&1; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y unzip curl
  curl -s "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
  unzip -q -o /tmp/awscliv2.zip -d /tmp
  /tmp/aws/install --update
fi

# 인스턴스 IAM 역할 자격으로 ECR 로그인 후 pull
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ECR"
docker pull "$IMAGE"