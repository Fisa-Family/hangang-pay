/**
 * 한강페이 Bank 부하 테스트
 *
 * 사전 준비:
 *   1. ./export_users.sh 로 users.json 생성
 *   2. merchants.json 생성 (export_users.sh의 MERCHANT_WALLET 옵션 또는 수동)
 *   3. k6 인스턴스에서: ulimit -n 65535
 *
 * 실행:
 *   k6 run -e BASE_URL=http://<bank-host>:8081 payment_load.js
 *
 * 환경변수:
 *   BASE_URL        - bank 서버 주소 (기본: http://localhost:8081)
 *   CHARGE_EVERY_N  - 결제 N번마다 충전 1회 (기본: 5)
 *   CANCEL_RATE     - 결제 후 취소 확률 0~1 (기본: 0.2)
 *   EXCHANGE_RATE   - 환전 확률 0~1 (기본: 0.05)
 *   VUS             - 최대 동시 VU 수 (기본: 50)
 *   DURATION        - steady-state 유지 시간 (기본: 2m)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// ── 설정 ────────────────────────────────────────────────────────────────────
const BASE_URL      = __ENV.BASE_URL       || 'http://localhost:8081';
const CHARGE_EVERY  = parseInt(__ENV.CHARGE_EVERY_N  || '5');
const CANCEL_RATE   = parseFloat(__ENV.CANCEL_RATE   || '0.2');
const EXCHANGE_RATE = parseFloat(__ENV.EXCHANGE_RATE || '0.05');
const MAX_VUS       = parseInt(__ENV.VUS      || '50');
const DURATION      = __ENV.DURATION || '2m';
const RAMP          = __ENV.RAMP     || '30s';

// ── 데이터 픽스처 ─────────────────────────────────────────────────────────────
// wallets.json  : bank_wallet 전체 (payment/cancel/charge/exchange 의 walletAddress)
// accounts.json : bank_account 전체 (charge/exchange 의 accountNumber + institutionId)
// merchants.json: 가맹점 지갑 목록 (payment/cancel 의 toWalletAddress)
//
// wallet ↔ account 는 bank DB에서 매핑 불가 → 독립 인덱스로 랜덤 할당.
// 잔액 부족으로 인한 400 응답도 테스트 결과에 포함됨.
const wallets = new SharedArray('wallets', function () {
  return JSON.parse(open('./wallets.json'));
});

const accounts = new SharedArray('accounts', function () {
  return JSON.parse(open('./accounts.json'));
});

const merchants = new SharedArray('merchants', function () {
  return JSON.parse(open('./merchants.json'));
});

// ── 커스텀 메트릭 ─────────────────────────────────────────────────────────────
const chargeErrors    = new Counter('charge_errors');
const paymentErrors   = new Counter('payment_errors');
const cancelErrors    = new Counter('cancel_errors');
const exchangeErrors  = new Counter('exchange_errors');
const chargeOk        = new Counter('charge_ok');
const paymentOk       = new Counter('payment_ok');
const cancelOk        = new Counter('cancel_ok');
const exchangeOk      = new Counter('exchange_ok');
const paymentDuration = new Trend('payment_duration', true);

// ── 시나리오 ──────────────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    load: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: RAMP, target: Math.floor(MAX_VUS * 0.5) },
        { duration: RAMP, target: MAX_VUS },
        { duration: DURATION, target: MAX_VUS },
        { duration: RAMP, target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed:  ['rate<0.05'],
    http_req_duration: ['p(95)<3000', 'p(99)<5000'],
    payment_errors:   ['count<50'],
  },
};

// ── 헬퍼 ──────────────────────────────────────────────────────────────────────
const HEADERS = { headers: { 'Content-Type': 'application/json' }, timeout: '15s' };

function post(path, body) {
  return http.post(`${BASE_URL}${path}`, JSON.stringify(body), HEADERS);
}

function isOk(res) {
  if (res.status !== 200) return false;
  try {
    return JSON.parse(res.body).isSuccess === true;
  } catch (_) {
    return false;
  }
}

// ── 각 거래 함수 ───────────────────────────────────────────────────────────────
// account(institutionId + accountNumber) 와 walletAddress 는 다른 유저일 수 있음.
// 잔액 부족 400은 테스트 결과의 일부로 취급.
function doCharge(account, walletAddress) {
  const amount = 10000;
  const res = post('/api/v1/transactions/charge', {
    transactionUuid: uuidv4(),
    institutionId:   account.institutionId,
    accountNumber:   account.accountNumber,
    walletAddress:   walletAddress,
    amount:          amount,
    mintAmount:      amount * 1.1,
  });
  const ok = check(res, { 'charge 200 + isSuccess': isOk });
  ok ? chargeOk.add(1) : chargeErrors.add(1);
  if (!ok) console.log(`[charge FAIL] vu=${__VU} status=${res.status} body=${res.body.slice(0, 200)}`);
}

// 결제 성공 시 transactionUuid 반환, 실패 시 null
function doPayment(fromWallet, toWallet) {
  const txUuid = uuidv4();
  const start = Date.now();
  const res = post('/api/v1/transactions/payment', {
    transactionUuid:   txUuid,
    fromWalletAddress: fromWallet,
    toWalletAddress:   toWallet,
    amount:            1000,
  });
  paymentDuration.add(Date.now() - start);
  const ok = check(res, { 'payment 200 + isSuccess': isOk });
  ok ? paymentOk.add(1) : paymentErrors.add(1);
  if (!ok) console.log(`[payment FAIL] vu=${__VU} uuid=${txUuid} status=${res.status} body=${res.body.slice(0, 200)}`);
  return ok ? txUuid : null;
}

function doCancel(userWallet, merchantWallet, originalUuid) {
  const res = post('/api/v1/transactions/cancel', {
    transactionUuid:         uuidv4(),
    originalTransactionUuid: originalUuid,
    fromWalletAddress:       merchantWallet,  // cancel: merchant → user 역방향
    toWalletAddress:         userWallet,
    amount:                  1000,
  });
  const ok = check(res, { 'cancel 200 + isSuccess': isOk });
  ok ? cancelOk.add(1) : cancelErrors.add(1);
  if (!ok) console.log(`[cancel FAIL] vu=${__VU} orig=${originalUuid} status=${res.status} body=${res.body.slice(0, 200)}`);
}

function doExchange(account, walletAddress) {
  const res = post('/api/v1/transactions/exchange', {
    transactionUuid: uuidv4(),
    institutionId:   account.institutionId,
    walletAddress:   walletAddress,
    accountNumber:   account.accountNumber,
    amount:          500,
  });
  const ok = check(res, { 'exchange 200 + isSuccess': isOk });
  ok ? exchangeOk.add(1) : exchangeErrors.add(1);
  if (!ok) console.log(`[exchange FAIL] vu=${__VU} status=${res.status} body=${res.body.slice(0, 200)}`);
}

// ── VU별 이터레이션 카운터 (모듈 스코프 = VU별 독립) ──────────────────────────
let iterCount = 0;

// ── 메인 ──────────────────────────────────────────────────────────────────────
export default function () {
  iterCount++;

  const wallet   = wallets[(__VU - 1) % wallets.length];
  const account  = accounts[(__VU - 1) % accounts.length];
  const merchant = merchants[(__VU - 1) % merchants.length];

  // 잔액 보충: CHARGE_EVERY 이터레이션마다 1회 충전
  // account ↔ wallet 이 같은 유저가 아닐 수 있음 → 잔액 부족 400도 테스트 포함
  if (iterCount % CHARGE_EVERY === 1) {
    doCharge(account, wallet.walletAddress);
    sleep(0.2);
  }

  // 결제
  const merchantAddr = merchant.walletAddress || merchant;
  const paymentUuid = doPayment(wallet.walletAddress, merchantAddr);
  sleep(0.1);

  // 취소 (확률적)
  if (paymentUuid !== null && Math.random() < CANCEL_RATE) {
    doCancel(wallet.walletAddress, merchantAddr, paymentUuid);
    sleep(0.1);
  }

  // 환전 (확률적, 드물게)
  if (Math.random() < EXCHANGE_RATE) {
    doExchange(account, wallet.walletAddress);
    sleep(0.1);
  }

  sleep(0.2);
}

// ── 테스트 종료 후 일관성 검사 안내 ───────────────────────────────────────────
export function handleSummary(data) {
  console.log('\n=== 부하 테스트 완료. 아래 SQL로 일관성 검사 실행 ===');
  console.log(`
-- 1. 제출됐지만 FAILED (체인에서 직접 txHash 상태 확인 필요)
SELECT id, idempotent_key, tx_hash, confirmed_at
FROM blockchain_ledger
WHERE status = 'FAILED' AND tx_hash IS NOT NULL;

-- 2. SUCCESS인데 txHash 없음 (AlreadyProcessed 발생 횟수)
SELECT COUNT(*) AS already_processed_count
FROM blockchain_ledger
WHERE status = 'SUCCESS' AND tx_hash IS NULL;

-- 3. 부하 종료 후에도 PENDING 잔존 (MQ 메시지 유실 의심)
SELECT COUNT(*) AS stuck_pending
FROM blockchain_ledger
WHERE status = 'PENDING'
  AND created_at < NOW() - INTERVAL 5 MINUTE;

-- 4. 상태별 분포 요약
SELECT status,
       COUNT(*)                          AS total,
       COUNT(tx_hash)                    AS with_txhash,
       COUNT(*) - COUNT(tx_hash)         AS without_txhash
FROM blockchain_ledger
GROUP BY status;
  `);

  return { stdout: JSON.stringify(data, null, 2) };
}
