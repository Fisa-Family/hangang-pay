import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

/**
 * 결제 VU1000 부하 테스트 (rate limit OFF/ON p99 비교용)
 *
 * 시나리오 (VU별 서로 다른 시드 유저)
 *   1. VU마다 1회 로그인 → 세션 쿠키 캐시
 *   2. iteration: 결제 intent 생성 → execute 1회
 *   3. execute 응답을 2xx / 429(차단) / 그 외로 분류, latency 수집
 *
 * 같은 스크립트로 두 번 실행한다 (차이는 BE 프로필뿐):
 *   [OFF] BE: --spring.profiles.active=local,loadtest,loadtest-off   (rate limit 꺼짐)
 *   [ON ] BE: --spring.profiles.active=local,loadtest,loadtest-on    (차단 튜닝)
 *
 * 실행
 *   k6 run k6/payment-loadtest.js
 *   k6 run -e TARGET_VUS=1000 -e HOLD=2m k6/payment-loadtest.js
 *
 * 환경변수
 *   BASE_URL          기본 http://localhost:8080
 *   TARGET_VUS        최대 VU 수, 기본 1000 (시드된 LOADTEST_USER_COUNT 이하)
 *   RAMP              ramp-up/down 구간, 기본 30s
 *   HOLD              최대 VU 유지 구간, 기본 2m
 *   PASSWORD          기본 password
 *   PAYMENT_PIN       기본 123456
 *   MERCHANT_COUNT    기본 1000 (seed된 가맹점 수)
 *   MERCHANT_PARTY_ID_BASE 기본 4 (local seed 1~3 이후 첫 loadtest 가맹점 partyId)
 *   AMOUNT            결제 금액, 기본 1000
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TARGET_VUS = Number(__ENV.TARGET_VUS || 1000);
const RAMP = __ENV.RAMP || '30s';
const HOLD = __ENV.HOLD || '2m';
// RATE 설정 시 open model(constant-arrival-rate)로 고정 RPS를 주입한다.
// 시스템 용량을 초과하는 부하를 흘려 OFF(포화)와 ON(셰딩) 차이를 명확히 본다.
const RATE = Number(__ENV.RATE || 0);
const PRE_VUS = Number(__ENV.PRE_VUS || 400);
const PASSWORD = __ENV.PASSWORD || 'password';
const PAYMENT_PIN = __ENV.PAYMENT_PIN || '123456';
const MERCHANT_COUNT = Number(__ENV.MERCHANT_COUNT || 1000);
const MERCHANT_PARTY_ID_BASE = Number(__ENV.MERCHANT_PARTY_ID_BASE || 4);
const AMOUNT = Number(__ENV.AMOUNT || 1);

// 결제 실행 관점 커스텀 메트릭
const executeLatency = new Trend('execute_latency', true); // execute 응답 latency 전체 (2xx+429 혼합)
const executeLatency2xx = new Trend('execute_latency_2xx', true); // 통과(2xx) 요청만 — 로드셰딩 효과 핵심 지표
const executeLatency429 = new Trend('execute_latency_429', true); // 차단(429) 요청만 (빠르게 거절)
const exec2xx = new Counter('exec_2xx'); // 결제 성공
const exec429 = new Counter('exec_429'); // rate limit 차단
const execOther = new Counter('exec_other'); // 4xx/5xx 등 그 외
const intentFailed = new Counter('intent_failed'); // intent 생성 실패(차단 포함)

// VU별 세션 쿠키 캐시 (모듈 스코프 — VU 인스턴스 내에서 유지)
const sessions = {};

const scenario = RATE
  ? {
      // open model: 완료와 무관하게 RATE req/s를 고정 주입
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: HOLD,
      preAllocatedVUs: PRE_VUS,
      maxVUs: PRE_VUS,
    }
  : {
      // closed model: VU 수 기준 ramp
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP, target: TARGET_VUS },
        { duration: HOLD, target: TARGET_VUS },
        { duration: RAMP, target: 0 },
      ],
      gracefulRampDown: '10s',
    };

export const options = {
  scenarios: { payment_load: scenario },
  // 429는 정상적인 차단이므로 실패로 보지 않는다. 관찰 목적의 threshold만 둔다.
  thresholds: {
    execute_latency: ['p(99)>=0'], // 항상 통과 — 요약에 p99를 출력시키기 위한 장치
  },
};

// VU를 시드된 유저(1..SEEDED)에 매핑. open model에서 VU 수가 시드 수를 넘으면 wrap.
const SEEDED = Number(__ENV.SEEDED || 300);
function phoneForVu(vuId) {
  const i = ((vuId - 1) % SEEDED) + 1;
  return '010' + String(i).padStart(8, '0'); // LoadTestDataInitializer의 String.format("010%08d", i)와 일치
}

function merchantPartyIdForVu(vuId) {
  const i = ((vuId - 1) % MERCHANT_COUNT) + 1;
  return MERCHANT_PARTY_ID_BASE + i - 1;
}

// VU당 1회 로그인 후 쿠키 반환 (캐시)
function loginOnce(vuId) {
  if (sessions[vuId]) {
    return sessions[vuId];
  }
  const res = http.post(
    `${BASE_URL}/api/v1/auth/users/login`,
    JSON.stringify({ phoneNumber: phoneForVu(vuId), password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
  );
  if (res.status !== 200) {
    return null;
  }
  const cookie = Object.entries(res.cookies)
    .map(([name, values]) => `${name}=${values[0].value}`)
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

  // 1. 결제 intent 생성 → transactionUuid 채번
  // amount를 iteration마다 고유하게 만든다. intent는 (userId+merchant+amount) 핑거프린트로 30초 내 중복을
  // dedup하므로, 동일 amount면 같은 transactionUuid로 수렴해 409가 발생하고 rate limiter 이전 단계에서 단락된다.
  const uniqueAmount = AMOUNT + (exec.scenario.iterationInTest % 1000000);
  const intentRes = http.post(
    `${BASE_URL}/api/v1/payment/intents`,
    JSON.stringify({ merchantPartyId: merchantPartyIdForVu(vuId), amount: uniqueAmount, itemName: 'k6 부하' }),
    { headers, tags: { name: 'create_intent' } },
  );
  if (intentRes.status !== 200 && intentRes.status !== 201) {
    intentFailed.add(1); // 429(intent 차단) 또는 그 외
    return;
  }
  const transactionUuid = intentRes.json('result.transactionUuid');

  // 2. 결제 실행 1회 → latency/상태 분류
  const execRes = http.post(
    `${BASE_URL}/api/v1/payment/${transactionUuid}/execute`,
    JSON.stringify({ paymentPin: PAYMENT_PIN }),
    { headers, tags: { name: 'execute_payment' } },
  );
  executeLatency.add(execRes.timings.duration);

  if (execRes.status >= 200 && execRes.status < 300) {
    exec2xx.add(1);
    executeLatency2xx.add(execRes.timings.duration);
  } else if (execRes.status === 429) {
    exec429.add(1);
    executeLatency429.add(execRes.timings.duration);
  } else {
    execOther.add(1);
  }

  check(execRes, { 'execute 응답 수신': (r) => r.status !== 0 });
}
