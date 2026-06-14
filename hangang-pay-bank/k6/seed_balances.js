/**
 * 부하 테스트 전 wallet 잔액 사전 충전 스크립트.
 * charge API를 통해 DB wallet balance + 온체인 mint 를 동시에 처리한다.
 *
 * 실행:
 *   k6 run -e BASE_URL=http://localhost:8081 seed_balances.js
 *
 * 완료 후 확인:
 *   SELECT MIN(balance), MAX(balance), AVG(balance) FROM bank_wallet;
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const CHARGE_AMOUNT = parseInt(__ENV.CHARGE_AMOUNT || '500000');  // 1인당 충전 금액

const wallets  = new SharedArray('wallets',  () => JSON.parse(open('./wallets.json')));
const accounts = new SharedArray('accounts', () => JSON.parse(open('./accounts.json')));

export const options = {
  // wallets 수만큼 1회씩만 실행
  scenarios: {
    seed: {
      executor: 'shared-iterations',
      vus: 20,
      iterations: wallets.length,
      maxDuration: '10m',
    },
  },
  thresholds: {
    // 충전 실패가 20% 넘으면 경고 (account 잔액 부족 등)
    http_req_failed: ['rate<0.2'],
  },
};

export default function (data) {
  const idx     = __ITER;  // 0-based, 전체 iteration 번호 (shared-iterations는 전역 카운터)
  const wallet  = wallets[idx % wallets.length];
  const account = accounts[idx % accounts.length];

  const res = http.post(
    `${BASE_URL}/api/v1/transactions/charge`,
    JSON.stringify({
      transactionUuid: uuidv4(),
      institutionId:   account.institutionId,
      accountNumber:   account.accountNumber,
      walletAddress:   wallet.walletAddress,
      amount:          CHARGE_AMOUNT,
      mintAmount:      Math.floor(CHARGE_AMOUNT * 1.1),
    }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '15s' }
  );

  const ok = check(res, { 'charge ok': (r) => r.status === 200 && JSON.parse(r.body).isSuccess });
  if (!ok) {
    console.log(`[seed FAIL] idx=${idx} wallet=${wallet.walletAddress} status=${res.status} body=${res.body.slice(0, 150)}`);
  }

  sleep(0.1);
}

export function handleSummary(data) {
  const total   = data.metrics['http_reqs']?.values?.count ?? 0;
  const failed  = data.metrics['http_req_failed']?.values?.passes ?? 0;
  console.log(`\n=== seed 완료: 총 ${total}건, 실패 ${failed}건 ===`);
  console.log('잔액 확인: SELECT MIN(balance), MAX(balance), COUNT(*) FROM bank_wallet;');
  return {};
}
