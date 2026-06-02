#!/bin/bash
set -Eeuo pipefail
REGION=ap-northeast-2
ECR=857291404781.dkr.ecr.${REGION}.amazonaws.com
IMAGE=${ECR}/hangang-pay-be:prod-latest

# 인스턴스 IAM 역할 자격으로 ECR 로그인 후 pull
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ECR"
docker pull "$IMAGE"