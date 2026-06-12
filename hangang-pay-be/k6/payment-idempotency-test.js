import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';

/**
 * 결제 멱등성 테스트
 *
 * 검증 시나리오
 *   1. 같은 transactionUuid로 동시 중복 execute → 성공은 단 1건, 나머지는 409 차단
 *   2. 결제 완료 후 같은 transactionUuid로 재시도 → 은행 재호출 없이 동일 스냅샷(승인번호) 반환
 *
 * 실행 예시 (Grafana Cloud k6로 결과 스트리밍)
 *   k6 cloud run --local-execution k6/payment-idempotency-test.js
 *
 * 환경변수
 *   BASE_URL           기본 http://localhost:8080
 *   PHONE_NUMBER       기본 01012345678  (LocalDataInitializer 시드 계정)
 *   PASSWORD           기본 password
 *   PAYMENT_PIN        기본 123456
 *   MERCHANT_PARTY_ID  기본 2
 *   DUPLICATES         burst당 동시 중복 요청 수, 기본 10
 *   ITERATIONS         intent 생성 횟수, 기본 3 (execute 레이트리밋 토큰이 5개라 5 이하 권장)
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PHONE_NUMBER = __ENV.PHONE_NUMBER || '01012345678';
const PASSWORD = __ENV.PASSWORD || 'password';
const PAYMENT_PIN = __ENV.PAYMENT_PIN || '123456';
const MERCHANT_PARTY_ID = Number(__ENV.MERCHANT_PARTY_ID || 2);
const DUPLICATES = Number(__ENV.DUPLICATES || 10);
const ITERATIONS = Number(__ENV.ITERATIONS || 3);

// 멱등성 관점 커스텀 메트릭 — Grafana Cloud k6 분석 탭에서 확인
const paymentSucceeded = new Counter('idempotency_payment_succeeded'); // 실제 결제 성공(승인번호 발급)
const duplicateBlocked = new Counter('idempotency_duplicate_blocked'); // 409 PAYMENT_ALREADY_PROCESSING
const snapshotReturned = new Counter('idempotency_snapshot_returned'); // 완료 후 재시도 → 동일 스냅샷 반환
const violationDetected = new Counter('idempotency_violation'); // 멱등성 깨짐(승인번호 2개 이상 등)
const unexpectedResponse = new Counter('idempotency_unexpected_response'); // 429, 5xx 등 시나리오 외 응답

export const options = {
  scenarios: {
    payment_idempotency: {
      executor: 'shared-iterations',
      vus: 1, // burst는 http.batch로 병렬 전송하므로 VU는 1개면 충분
      iterations: ITERATIONS,
      maxDuration: '5m',
    },
  },
  // DUPLICATES개의 요청이 전부 동시에 나가도록 batch 병렬 한도 상향 (기본 host당 6)
  batch: DUPLICATES,
  batchPerHost: DUPLICATES,
  thresholds: {
    idempotency_violation: ['count==0'], // 멱등성 위반이 1건이라도 있으면 테스트 실패
    checks: ['rate==1'],
  },
  cloud: {
    name: 'payment-idempotency',
  },
};

export function setup() {
  // 1. 시드 소비자 계정으로 로그인 → 세션 쿠키 확보
  const res = http.post(
    `${BASE_URL}/api/v1/auth/users/login`,
    JSON.stringify({ phoneNumber: PHONE_NUMBER, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  if (res.status !== 200) {
    fail(`로그인 실패: status=${res.status} body=${res.body}`);
  }

  // 2. 세션 쿠키를 VU에 전달 (쿠키 이름이 JSESSIONID든 SESSION이든 모두 수용)
  const cookie = Object.entries(res.cookies)
    .map(([name, values]) => `${name}=${values[0].value}`)
    .join('; ');

  if (!cookie) {
    fail('로그인 응답에 세션 쿠키가 없습니다.');
  }

  return { cookie };
}

export default function (data) {
  const headers = { 'Content-Type': 'application/json', Cookie: data.cookie };

  // 1. 결제 intent 생성 → transactionUuid 채번
  const intentRes = http.post(
    `${BASE_URL}/api/v1/payment/intents`,
    JSON.stringify({
      merchantPartyId: MERCHANT_PARTY_ID,
      amount: 1000,
      itemName: 'k6 멱등성 테스트',
    }),
    { headers, tags: { name: 'create_intent' } },
  );

  if (
    !check(intentRes, {
      'intent 생성 성공': (r) => r.status === 201 || r.status === 200,
    })
  ) {
    unexpectedResponse.add(1);
    return;
  }

  const transactionUuid = intentRes.json('result.transactionUuid');
  const executeUrl = `${BASE_URL}/api/v1/payment/${transactionUuid}/execute`;
  const executeBody = JSON.stringify({ paymentPin: PAYMENT_PIN });
  const executeParams = { headers, tags: { name: 'execute_payment' } };

  // 2. 같은 transactionUuid로 동시 중복 execute burst
  const responses = http.batch(
    Array.from({ length: DUPLICATES }, () => ['POST', executeUrl, executeBody, executeParams]),
  );

  // 3. burst 응답 분류 — 200(성공/스냅샷) vs 409(중복 차단) vs 그 외
  const approvalNumbers = new Set();
  let success200 = 0;
  let blocked = 0;

  for (const res of responses) {
    if (res.status === 200) {
      success200 += 1;
      const approval = res.json('result.approvalNumber');
      if (approval) {
        approvalNumbers.add(approval);
      }
    } else if (res.status === 409) {
      blocked += 1;
      duplicateBlocked.add(1);
    } else {
      unexpectedResponse.add(1);
      console.error(`예상 외 응답: status=${res.status} body=${res.body}`);
    }
  }

  // 4. 핵심 불변식 — 동시 중복 N건에서 승인번호는 정확히 1개만 발급
  const burstOk = check(null, {
    'burst: 승인번호가 정확히 1개': () => approvalNumbers.size === 1,
    'burst: 200/409 외 응답 없음': () => success200 + blocked === DUPLICATES,
  });

  if (approvalNumbers.size > 1) {
    violationDetected.add(approvalNumbers.size - 1); // 승인번호가 2개 이상 = 이중 결제
  }
  if (approvalNumbers.size === 1) {
    paymentSucceeded.add(1);
  }
  if (!burstOk) {
    console.error(
      `burst 결과: 승인번호 ${approvalNumbers.size}개, 409 차단 ${blocked}건 / ${DUPLICATES}건`,
    );
  }

  const firstApproval = approvalNumbers.size === 1 ? [...approvalNumbers][0] : null;

  // 5. 결제 완료 후 재시도 → 매번 동일 승인번호 스냅샷이 반환되는지 검증 (은행 재호출 없음)
  for (let i = 0; i < 3; i += 1) {
    sleep(0.5);
    const retryRes = http.post(executeUrl, executeBody, {
      headers,
      tags: { name: 'retry_after_success' },
    });

    if (retryRes.status === 200) {
      const approval = retryRes.json('result.approvalNumber');
      const same = check(retryRes, {
        'retry: 동일 승인번호 스냅샷 반환': () => firstApproval !== null && approval === firstApproval,
      });
      if (same) {
        snapshotReturned.add(1);
      } else {
        violationDetected.add(1);
        console.error(`스냅샷 불일치: 최초=${firstApproval}, 재시도=${approval}`);
      }
    } else if (retryRes.status === 409) {
      // 은행 응답 지연으로 아직 PROCESSING이면 409 — 다음 루프에서 재확인
      duplicateBlocked.add(1);
    } else {
      unexpectedResponse.add(1);
    }
  }
}
