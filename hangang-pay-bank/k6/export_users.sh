#!/bin/bash
# DB에서 테스트 데이터를 추출해 wallets.json / accounts.json / merchants.json 으로 저장.
# MySQL이 Docker 컨테이너 안에서 실행 중임을 가정.
#
# 사용법:
#   MERCHANT_WALLET=0x... ./export_users.sh
#
# 환경변수 (기본값 있음):
#   MYSQL_CONTAINER  - Docker 컨테이너 이름 (기본: bank-mysql)
#   DB_NAME          - 데이터베이스 이름 (기본: hangang_pay_bank_onprem_test)
#   DB_USER          - MySQL 유저 (기본: root)
#   DB_PASS          - MySQL 패스워드 (기본: 1234)
#   USER_LIMIT       - 추출할 최대 행 수 (기본: 1000)
#   MERCHANT_WALLET  - 가맹점 지갑 주소

set -euo pipefail

CONTAINER="${MYSQL_CONTAINER:-bank-mysql}"
DB="${DB_NAME:-hangang_pay_bank_onprem_test}"
USER="${DB_USER:-root}"
PASS="${DB_PASS:-1234}"
LIMIT="${USER_LIMIT:-1000}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "[INFO] Docker 컨테이너 '$CONTAINER' MySQL에서 데이터 추출 중..."

run_query() {
  docker exec "$CONTAINER" \
    mysql -u"$USER" -p"$PASS" "$DB" \
    --skip-column-names --silent \
    -e "$1" 2>/dev/null
}

# wallets.json: payment/cancel/charge/exchange 에서 walletAddress 사용
# 가맹점 지갑(MERCHANT_WALLET)은 제외 — payment의 toWallet과 fromWallet이 같으면 wallet_ledger unique key 충돌
MERCHANT_EXCLUDE=""
if [ -n "${MERCHANT_WALLET:-}" ]; then
  MERCHANT_EXCLUDE="AND wallet_address != '${MERCHANT_WALLET}'"
fi
WALLET_RESULT=$(run_query "
SELECT JSON_ARRAYAGG(JSON_OBJECT('institutionId', institution_id, 'walletAddress', wallet_address))
FROM (SELECT institution_id, wallet_address FROM bank_wallet WHERE 1=1 ${MERCHANT_EXCLUDE} LIMIT ${LIMIT}) t;
")

if [ -z "$WALLET_RESULT" ] || [ "$WALLET_RESULT" = "NULL" ]; then
  echo "[ERROR] bank_wallet 쿼리 결과가 비어있습니다." >&2
  exit 1
fi
echo "$WALLET_RESULT" > "$SCRIPT_DIR/wallets.json"
WALLET_COUNT=$(echo "$WALLET_RESULT" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "?")
echo "[OK] wallets.json 생성 완료. 지갑 수: $WALLET_COUNT"

# accounts.json: charge/exchange 에서 accountNumber + institutionId 사용
# wallet ↔ account 매핑 불필요 — charge/exchange 는 잔액 부족 시 400이 나는 것도 테스트의 일부
ACCOUNT_RESULT=$(run_query "
SELECT JSON_ARRAYAGG(JSON_OBJECT('institutionId', institution_id, 'accountNumber', account_number))
FROM (SELECT institution_id, account_number FROM bank_account LIMIT ${LIMIT}) t;
")

if [ -z "$ACCOUNT_RESULT" ] || [ "$ACCOUNT_RESULT" = "NULL" ]; then
  echo "[ERROR] bank_account 쿼리 결과가 비어있습니다." >&2
  exit 1
fi
echo "$ACCOUNT_RESULT" > "$SCRIPT_DIR/accounts.json"
ACCOUNT_COUNT=$(echo "$ACCOUNT_RESULT" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "?")
echo "[OK] accounts.json 생성 완료. 계좌 수: $ACCOUNT_COUNT"

# merchants.json: payment/cancel 의 toWalletAddress (가맹점)
MERCHANT="${MERCHANT_WALLET:-}"
if [ -n "$MERCHANT" ]; then
  echo "[\"$MERCHANT\"]" > "$SCRIPT_DIR/merchants.json"
  echo "[OK] merchants.json 생성 완료."
else
  echo "[WARN] MERCHANT_WALLET 환경변수가 없습니다."
  echo "       수동으로 생성: echo '[\"0x...\"]' > merchants.json"
fi
