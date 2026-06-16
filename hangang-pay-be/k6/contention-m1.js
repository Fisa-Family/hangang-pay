import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

/**
 * 가맹점 row-lock 경합 측정 — 가맹점 1개 (VU 400 전부 한 가맹점 → 최대 경합)
 *
 * 실행 (k6 메트릭도 Grafana로):
 *   K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
 *   k6 run -o experimental-prometheus-rw k6/contention-m1.js
 * 콘솔만:
 *   k6 run k6/contention-m1.js
 *
 * 전제: besu + bank(8081) + BE(8080, loadtest-off) + 유저/가맹점 400 시드, 유저 잔액 충전.
 * 주의: amount 고정이라 같은 유저→같은 가맹점 반복은 30초 dedup에 걸릴 수 있음(파일별 1/2/3/4로 분리).
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 400);
const HOLD = __ENV.HOLD || '30s';
const SEEDED = Number(__ENV.SEEDED || 400);
const MERCHANT_PARTY_ID_BASE = Number(__ENV.MERCHANT_PARTY_ID_BASE || 4);
const PASSWORD = __ENV.PASSWORD || 'password';
const PAYMENT_PIN = __ENV.PAYMENT_PIN || '123456';

// ★ 이 파일 고정값
const MERCHANT_COUNT = 1;
const AMOUNT = Number(__ENV.AMOUNT || 1); // 1원

const exec2xx = new Counter('exec_2xx');
const execOther = new Counter('exec_other');
const intentFailed = new Counter('intent_failed');
const execLatency = new Trend('execute_latency_2xx', true);

export const options = {
  scenarios: { contention: { executor: 'constant-vus', vus: VUS, duration: HOLD } },
  tags: { merchants: String(MERCHANT_COUNT) }, // Grafana 런 구분
};

const sessions = {};
function phoneForVu(vuId) {
  const i = ((vuId - 1) % SEEDED) + 1;
  return '010' + String(i).padStart(8, '0');
}
function merchantPartyIdForVu(vuId) {
  const i = ((vuId - 1) % MERCHANT_COUNT) + 1;
  return MERCHANT_PARTY_ID_BASE + i - 1;
}
function loginOnce(vuId) {
  if (sessions[vuId]) return sessions[vuId];
  const res = http.post(
    `${BASE_URL}/api/v1/auth/users/login`,
    JSON.stringify({ phoneNumber: phoneForVu(vuId), password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
  );
  if (res.status !== 200) return null;
  const cookie = Object.entries(res.cookies)
    .map(([n, v]) => `${n}=${v[0].value}`)
    .join('; ');
  sessions[vuId] = cookie || null;
  return sessions[vuId];
}

export default function () {
  const vuId = exec.vu.idInTest;
  const cookie = loginOnce(vuId);
  if (!cookie) {
    intentFailed.add(1);
    return;
  }
  const headers = { 'Content-Type': 'application/json', Cookie: cookie };

  const intentRes = http.post(
    `${BASE_URL}/api/v1/payment/intents`,
    JSON.stringify({ merchantPartyId: merchantPartyIdForVu(vuId), amount: AMOUNT, itemName: 'contention' }),
    { headers, tags: { name: 'create_intent' } },
  );
  if (intentRes.status !== 200 && intentRes.status !== 201) {
    intentFailed.add(1);
    return;
  }
  const uuid = intentRes.json('result.transactionUuid');

  const execRes = http.post(
    `${BASE_URL}/api/v1/payment/${uuid}/execute`,
    JSON.stringify({ paymentPin: PAYMENT_PIN }),
    { headers, tags: { name: 'execute_payment' } },
  );
  execLatency.add(execRes.timings.duration);
  if (execRes.status >= 200 && execRes.status < 300) exec2xx.add(1);
  else execOther.add(1);
}
