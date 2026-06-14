import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';


// 1. 같은 요청을 동시에 3회 전송 시, 실제 처리가 1번만 일어나는가?
// 2. 나머지 요청은 409 또는 동일 snapshot으로 수렴하는가?
// 3. 처리가 끝난 뒤 또 쏘면 같은 결과(스냅샷)이 오는가?


/** 
 *                 -----흐름마다 코드가 다름------
 *
 *    흐름   멱등키(uuid)        분산 락   '처리 1번'의 증거 필드
 *    충전   FE가 직접 생성       없음     result.chargedAt + result.walletBalance
 *    결제   서버가 intent에서 발급  있음     result.approvalNumber
 *    취소   원거래 id 자체가 키     있음     result.transactionUuid(취소 거래)
 *    환전   FE가 직접 생성       없음     result.transactionId
 */

const BASE_URL = 'https://hangang-pay.cloud';
const USER_PHONE = __ENV.USER_PHONE || '01012121212';      // 시드 소비자
const MERCHANT_BIZ_NO = __ENV.MERCHANT_BIZ_NO || '2345678901'; // 시드 가맹점
const USER_PASSWORD = __ENV.PASSWORD || 'password!12';
const MERCHANT_PASSWORD = __ENV.PASSWORD || 'password1!';
const PAYMENT_PIN = __ENV.PAYMENT_PIN || '123456';
const USER_PARTY_ID = Number(1)
const MERCHANT_PARTY_ID = Number(10);
const CHARGE_ACCOUNT_ID = Number(4);
const CHARGE_INSTITUTION_ID = Number(3);
const DUPLICATES = Number(10);  // 동시 중복 요청 수
const ITERATIONS = Number(1);   // 전체 흐름 반복 횟수
const FLOW = 'charge';   // charge | payment | cancel | exchange | all


// 커스텀 메트릭
const burst2xx = new Counter('idempotency_burst_2xx');                         // burst 중 2xx 응답
const burstBlocked409 = new Counter('idempotency_burst_blocked_409');          // burst 중 409 중복 차단
const burstUnexpected = new Counter('idempotency_burst_unexpected_response');  // burst 중 예상치 못한 응답
const executedOnce = new Counter('idempotency_executed_once');                 // burst 결과 식별자 1개 수렴
const retrySnapshotReturned = new Counter('idempotency_retry_snapshot_returned'); // retry 동일 snapshot 반환
const retryBlocked409 = new Counter('idempotency_retry_blocked_409');          // retry 시 아직 PROCESSING
const retryUnexpected = new Counter('idempotency_retry_unexpected_response');  // retry 중 예상치 못한 응답
const violationDetected = new Counter('idempotency_violation');                // 멱등성 깨짐

// 실행 옵션
export const options = {
  scenarios: {
    idempotency_all: {
      executor: 'shared-iterations', // 정해진 횟수만 돌리고 끝
      vus: 1,                        // 동시성은 http.batch가 만든다
      iterations: ITERATIONS,
      maxDuration: '10m',
    },
  },
  // ★★★ 가장 중요한 함정 ★★★
  // batch 기본 동시 한도는 host당 6. 10발 쏴도 6+4로 쪼개져 "동시"가 깨지고
  // 멱등성이 깨져도 테스트가 통과해버린다(거짓 음성). 한도를 DUPLICATES까지 올린다.
  batch: DUPLICATES,
  batchPerHost: DUPLICATES,
  maxRedirects: 0,

  // threshold = 합격 기준. 위반 1건이라도 생기면 k6 자체가 실패(exit 1) → CI에 붙이기 좋음.
  thresholds: {
    idempotency_violation: ['count==0'],
    checks: ['rate==1'],
  },
  cloud: { name: 'idempotency-all' },
};

/* [H1] UUID 생성 — 충전/환전은 FE가 멱등키를 만드는 설계라 k6가 FE 역할로 생성 */
function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/* [H2] 로그인 → 세션 쿠키 문자열 반환 (JWT 아님, Spring Session + Redis) */
function login(path, body) {
  http.cookieJar().clear(BASE_URL);
  const res = http.post(`${BASE_URL}${path}`, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
  });
  if (res.status !== 200 || !isJsonResponse(res)) {
    fail(
      `로그인 실패: ${path} status=${res.status} contentType=${header(res, 'Content-Type')} body=${bodyPreview(res.body)}`,
    );
  }
  // res.cookies = { 쿠키이름: [{value,...}] } → "이름=값" 문자열로 조립
  const cookie = Object.entries(res.cookies)
    .map(([name, values]) => `${name}=${values[0].value}`)
    .join('; ');
  if (!cookie) fail(`세션 쿠키 없음: ${path}`);
  return cookie;
}

// [H3] 멱등성 burst 검증 — 이 테스트의 심장. 4개 흐름 모두 이 함수로 검증.
// N 번 요청 -> 실제 처리는 1번 
// 처리 1번의 증거는 identitySelector
// return 값: 첫 처리의 식별자 / 실패시 null
function extractIdentity(res, identitySelector) {
  if (typeof identitySelector === 'function') {
    return identitySelector(res);
  }
  const id = res.json(identitySelector);
  return id !== null && id !== undefined ? String(id) : null;
}

function isJsonResponse(res) {
  const contentType = res.headers['Content-Type'] || res.headers['content-type'] || '';
  return contentType.includes('application/json');
}

function header(res, name) {
  return res.headers[name] || res.headers[name.toLowerCase()] || '';
}

function bodyPreview(body) {
  return String(body || '').replace(/\s+/g, ' ').slice(0, 240);
}

function chargeSnapshotIdentity(res) {
  const chargedAt = res.json('result.chargedAt');
  const walletBalance = res.json('result.walletBalance');
  if (chargedAt === null || chargedAt === undefined) {
    console.error(`[charge] chargedAt 없음: body=${res.body}`);
    return null;
  }
  if (walletBalance === null || walletBalance === undefined) {
    console.error(`[charge] walletBalance 없음: body=${res.body}`);
    return null;
  }
  return JSON.stringify({ chargedAt, walletBalance });
}

function verifyBurst(flow, url, body, headers, identitySelector) {
  const params = { headers, tags: { name: `${flow}_execute` } };

  // (1) 같은 요청 DUPLICATES개를 한꺼번에 전송. batch는 전부 돌아올 때까지 대기.
  const responses = http.batch(
    Array.from({ length: DUPLICATES }, () => ['POST', url, body, params]),
  );

  // (2) 응답 분류: 2xx(처리됨) / 409(중복차단) / 그 외(이상)
  const identities = new Set(); // 처리된 응답의 식별자 — 정상이면 size===1
  let ok2xx = 0;
  let blocked = 0;
  for (const res of responses) {
    if (res.status >= 200 && res.status < 300) {
      if (!isJsonResponse(res)) {
        burstUnexpected.add(1, { flow });
        console.error(
          `[${flow}] JSON이 아닌 2xx 응답: url=${res.url}, status=${res.status}, contentType=${header(res, 'Content-Type')}, location=${header(res, 'Location')}, body=${bodyPreview(res.body)}`,
        );
        continue;
      }
      ok2xx += 1;
      burst2xx.add(1, { flow });
      const id = extractIdentity(res, identitySelector);
      if (id !== null) identities.add(id);
    } else if (res.status === 409) {
      blocked += 1;
      burstBlocked409.add(1, { flow });
    } else {
      burstUnexpected.add(1, { flow });
      console.error(
        `[${flow}] 예상 외 응답: url=${res.url}, status=${res.status}, contentType=${header(res, 'Content-Type')}, location=${header(res, 'Location')}, body=${bodyPreview(res.body)}`,
      );
    }
  }

  // (3) 핵심 불변식 검증
  const passed = check(null, {
    [`${flow} burst: 처리 식별자가 정확히 1개`]: () => identities.size === 1,
    [`${flow} burst: 2xx/409 외 응답 없음`]: () => ok2xx + blocked === DUPLICATES,
  });

  // 식별자 2개 이상 = 같은 멱등키인데 처리 2번 = 이중 결제 = 멱등성 깨짐
  if (identities.size > 1) {
    violationDetected.add(identities.size - 1, { flow });
    console.error(`[${flow}] ! 위반 식별자 ${identities.size}개: ${[...identities]}`);
  }
  if (identities.size === 1) executedOnce.add(1, { flow });
  if (!passed) console.error(`[${flow}] burst: 2xx=${ok2xx}, 409=${blocked}`);

  return identities.size === 1 ? [...identities][0] : null;
}


/* 
 * [H4] 재시도 스냅샷 검증 — "응답 유실 후 재시도" 재현.
 * 처리 끝난 키로 다시 쏘면 새 처리 없이 "아까 그 결과"가 와야 한다. 
 */
function verifyRetrySnapshot(flow, url, body, headers, identitySelector, firstId) {
  if (firstId === null) return; // burst 실패 시 비교 기준 없음 → skip
  // sleep 없이 1번만 재요청. 스냅샷은 burst가 끝난 시점에 이미 Redis에 저장돼 있다.
  const res = http.post(url, body, { headers, tags: { name: `${flow}_retry` } });
  if (res.status >= 200 && res.status < 300) {
    if (!isJsonResponse(res)) {
      retryUnexpected.add(1, { flow });
      console.error(
        `[${flow}] retry JSON이 아닌 2xx 응답: url=${res.url}, status=${res.status}, contentType=${header(res, 'Content-Type')}, location=${header(res, 'Location')}, body=${bodyPreview(res.body)}`,
      );
      return;
    }
    const id = extractIdentity(res, identitySelector);
    const same = check(res, {
      [`${flow} retry: 동일 스냅샷 반환`]: () => id === firstId,
    });
    if (same) retrySnapshotReturned.add(1, { flow });
    else {
      violationDetected.add(1, { flow });
      console.error(`[${flow}] 스냅샷 불일치: 최초=${firstId}, 재시도=${id}`);
    }
  } else if (res.status === 409) {
    retryBlocked409.add(1, { flow }); // 아직 PROCESSING (은행 지연 시)
  } else {
    retryUnexpected.add(1, { flow });
  }
}

/* ============================== 흐름별 테스트 ============================== */

/* [환전] FE가 uuid 생성 → intent → burst → 재시도.
 *  자격(최근 충전액 60% 사용) 미달이면 intent 거절되니 경고 후 skip. */
function testExchange(headers) {
  const transactionUuid = uuidv4();
  const intentRes = http.post(
    `${BASE_URL}/api/v1/exchange/intents`,
    JSON.stringify({ transactionUuid, amount: 1000 }),
    { headers, tags: { name: 'exchange_intent' } },
  );
  if (intentRes.status !== 200) {
    console.warn(`[exchange] intent 거절(자격 미달 가능): ${intentRes.status} — skip`);
    return;
  }
  const url = `${BASE_URL}/api/v1/exchange/${transactionUuid}/execute`;
  const body = JSON.stringify({ paymentPin: PAYMENT_PIN });
  const first = verifyBurst('exchange', url, body, headers, 'result.transactionId');
  verifyRetrySnapshot('exchange', url, body, headers, 'result.transactionId', first);
}

/* [충전] FE가 uuid 생성 → intent(금액·계좌 바인딩) → burst → 재시도.
 *  분산 락 없이 Redis 멱등 레코드 단독 방어 → 동시성 검증이 가장 중요한 흐름. */
function testCharge(headers) {
  const transactionUuid = uuidv4();
  const intentRes = http.post(
    `${BASE_URL}/api/v1/charge/intents`,
    JSON.stringify({
      transactionUuid,
      institutionId: CHARGE_INSTITUTION_ID,
      accountId: CHARGE_ACCOUNT_ID,
      amount: 10000,
    }),
    { headers, tags: { name: 'charge_intent' } },
  );
  if (!check(intentRes, { 'charge intent 성공': (r) => r.status === 200 && isJsonResponse(r) })) {
    burstUnexpected.add(1, { flow: 'charge' });
    console.error(
      `[charge] intent 실패: url=${intentRes.url}, status=${intentRes.status}, contentType=${header(intentRes, 'Content-Type')}, body=${bodyPreview(intentRes.body)}`,
    );
    return;
  }
  const url = `${BASE_URL}/api/v1/charge/${transactionUuid}/execute`;
  const body = JSON.stringify({ paymentPin: PAYMENT_PIN });
  const first = verifyBurst('charge', url, body, headers, chargeSnapshotIdentity);
  verifyRetrySnapshot('charge', url, body, headers, chargeSnapshotIdentity, first);
}

/* [결제] 서버가 uuid 발급 → intent → burst → 재시도. 승인번호를 취소에 넘김. */
function testPayment(headers) {
  const intentRes = http.post(
    `${BASE_URL}/api/v1/payment/intents`,
    JSON.stringify({ merchantPartyId: MERCHANT_PARTY_ID, amount: 1000, itemName: 'k6 멱등성' }),
    { headers, tags: { name: 'payment_intent' } },
  );
  if (!check(intentRes, { 'payment intent 성공': (r) => r.status === 200 || r.status === 201 })) {
    burstUnexpected.add(1, { flow: 'payment' });
    console.error(`[payment] intent 실패: ${intentRes.status} body=${intentRes.body}`);
    return null;
  }
  // 결제만 서버가 uuid를 채번해 응답에 내려줌 → 받아서 사용
  const transactionUuid = intentRes.json('result.transactionUuid');
  const url = `${BASE_URL}/api/v1/payment/${transactionUuid}/execute`;
  const body = JSON.stringify({ paymentPin: PAYMENT_PIN });
  const approvalNumber = verifyBurst('payment', url, body, headers, 'result.approvalNumber');
  verifyRetrySnapshot('payment', url, body, headers, 'result.approvalNumber', approvalNumber);
  return approvalNumber;
}

/* [취소] 승인번호로 원거래 id 역산 → cancel burst → 재시도. 가맹점 세션으로 호출. */
function testCancel(merchantHeaders, approvalNumber) {
  if (!approvalNumber) {
    console.warn('[cancel] 선행 결제 실패로 skip');
    return;
  }
  // 승인번호 APV-YYYY-NNNNNNNN, 끝 8자리 = 원결제 transaction.id (zero padding)
  const transactionId = parseInt(approvalNumber.split('-')[2], 10);
  // 취소의 멱등키는 별도 uuid가 아니라 "원거래 자체" (같은 결제를 두 번 환불 금지)
  const url = `${BASE_URL}/api/v1/merchant/payments/${transactionId}/cancel`;
  const body = JSON.stringify({ paymentPin: PAYMENT_PIN });
  const first = verifyBurst('cancel', url, body, merchantHeaders, 'result.transactionUuid');
  verifyRetrySnapshot('cancel', url, body, merchantHeaders, 'result.transactionUuid', first);
}

/* ============================== 시나리오 본체 ============================== */

// setup()은 테스트 시작 시 1번 실행되고, 반환값이 default 함수의 data 인자로 전달된다.
export function setup() {
  const userCookie = login('/api/v1/auth/users/login', {
    phoneNumber: USER_PHONE,
    password: USER_PASSWORD,
  });
  const merchantCookie = login('/api/v1/auth/merchants/login', {
    businessNumber: MERCHANT_BIZ_NO,
    password: MERCHANT_PASSWORD,
  });
  return { userCookie, merchantCookie };   // ← 이 반환값이 default(data)로 들어감
}

export default function (data) {
  const userHeaders = { 'Content-Type': 'application/json', Cookie: data.userCookie };
  const merchantHeaders = { 'Content-Type': 'application/json', Cookie: data.merchantCookie };

  // FLOW 환경변수로 원하는 흐름만 실행
  if (FLOW === 'exchange' || FLOW === 'all') {
    testExchange(userHeaders);
  }
  if (FLOW === 'charge' || FLOW === 'all') {
    testCharge(userHeaders);
  }
  if (FLOW === 'payment' || FLOW === 'cancel' || FLOW === 'all') {
    // 취소는 결제 결과(승인번호)가 있어야 하니, cancel을 돌리려면 payment도 같이 돌아야 함
    const approvalNumber = testPayment(userHeaders);
    if (FLOW === 'cancel' || FLOW === 'all') {
      testCancel(merchantHeaders, approvalNumber);
    }
  }
}
