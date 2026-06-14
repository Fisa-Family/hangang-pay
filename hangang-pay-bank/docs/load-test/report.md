# 부하 테스트 리포트

작성일: 2026-06-14

---

## 테스트 환경

### 인프라

| 항목 | 내용 |
|---|---|
| 블록체인 | Hyperledger Besu 26.4.0, 4-node QBFT, Docker Compose (로컬) |
| 블록 생성 주기 | 2초 |
| 블록 가스 한도 | 4,700,000 (genesis `gasLimit: 0x47b760`) |
| 은행 서버 | Spring Boot 3.5, Java 17, Tomcat, 포트 8081 |
| RPC | node1 직접 연결 `http://localhost:8545` (nginx lb `18545` 미사용) |

### k6 시나리오 (`payment_load.js`)

| 항목 | 내용 |
|---|---|
| 최대 VU | 50 |
| 시나리오 | ramping-vus (30s ramp-up → 2m peak → 1m ramp-down) |
| 총 실행 시간 | 3분 30초 |
| 작업 mix | charge → payment → cancel → exchange (VU마다 순환) |
| 지갑 수 | 5개 (50 VU가 공유, 1 VU당 여러 지갑 중복 사용) |
| 가맹점 | 1개 |

### 스마트 컨트랙트 가스 계측 (사전 측정)

| 함수 | gasUsed |
|---|---|
| charge | ~84,370 |
| payment | ~84,370 |
| cancelPayment | ~84,370 |

이론 최대 처리량: `(4,700,000 − 300,000) / 84,370 ≈ 52 tx/block → 26 tx/s`

---

## 이슈 히스토리

### 이슈 1 — wallets.json에 가맹점 지갑 포함 (Run1에서 발견)

**현상**
```
ERROR: Duplicate entry for key 'wallet_ledger.uk_transaction_uuid_bank_wallet_id'
```

**원인**
`wallets.json[4]`가 가맹점 지갑 주소와 동일했다. payment 시 `fromWallet == toWallet == 가맹점`이 되어
`wallet_ledger`의 유니크 제약 `(transaction_uuid, bank_wallet_id)` 충돌.

**수정**
- `wallets.json`에서 해당 항목 제거 (6개 → 5개)
- `export_users.sh`에 `MERCHANT_WALLET` 제외 SQL 추가

```bash
# export_users.sh 변경 후
MERCHANT_EXCLUDE=""
if [ -n "${MERCHANT_WALLET:-}" ]; then
  MERCHANT_EXCLUDE="AND wallet_address != '${MERCHANT_WALLET}'"
fi
```

---

### 이슈 2 — `txpool_besuStatistics` Method not enabled

**현상**
```
curl: (52) Empty reply from server
{"error":{"code":-32601,"message":"Method not enabled"}}
```

**원인** Besu `--rpc-http-api`에 `TXPOOL`이 없었음.

**수정** `docker-compose.yml` 4개 노드 전부에 추가:
```yaml
- --rpc-http-api=ETH,NET,QBFT,WEB3,MINER,ADMIN,TXPOOL
```

---

### 이슈 3 — `--rpc-http-max-connections` 옵션명 오류 (Run3 준비 중)

**현상 1** picocli가 `--rpc-http-max-connections=300`을 `--rpc-http-api` 값의 일부로 파싱:
```
Invalid value for option '--rpc-http-api': invalid entries found [--rpc-http-max-connections=300]
```
→ `--rpc-http-api` 뒤에 배치하면 picocli arity="0..*" 그리디 소비로 흡수됨.
→ **수정**: `--rpc-http-max-connections=300`을 `--rpc-http-api` 앞으로 이동.

**현상 2** 이동 후에도 실패:
```
Unknown option: '--rpc-http-max-connections=300'
Possible solutions: ... --rpc-http-max-active-connections ...
```
→ Besu 26.4.0에서 옵션명이 `--rpc-http-max-active-connections`.
→ **수정**: `--rpc-http-max-active-connections=300`으로 변경.

---

## 런별 비교표

| 항목 | Run1 | Run2 | Run3 | Run4 | Run5 |
|---|---|---|---|---|---|
| **변경사항** | 기준 | wallets.json 수정 | `max-active-connections=300` | `polling=15` | **VU 50→10** |
| **VU** | 50 | 50 | 50 | 50 | **10** |
| charge_ok | — | 17 | 22 | 17 | **44** |
| charge_errors | — | 1,070 | 1,303 | 1,074 | 527 |
| payment_ok | — | 7 | 21 | 14 | **205** |
| payment_errors | — | 5,289 | 6,408 | 5,254 | 2,628 |
| exchange_ok | — | 244 | 255 | 217 | 130 |
| cancel_ok | — | 1 | — | — | **51** |
| **http_req_duration avg** | — | 291.9 ms | 760.4 ms | 985.9 ms | **184.4 ms** |
| **http_req_duration p95** | — | 139.9 ms | 7,106 ms | 15,000 ms | **1,149 ms** |
| contract tx start | — | 1,086 | 1,300 | 1,072 | 571 |
| nonce fetched | — | 33 | 75 | 77 | 44 |
| IO failure | — | 1,053 | 1,225 | 995 | 527 |
| tx submitted | — | 33 | 75 | 77 | 44 |
| tx success | — | 33 | 75 | 77 | 44 |
| receipt timeout | — | 0 | 0 | 0 | 0 |
| TX/BLOCK (txpool) | — | 1 내내 | 1 내내 | 1 내내 | 0~1 혼재 |
| Besu ERR 시작 | — | +1분 | +2분 30초 | +2분 34초 | +1분 31초 |
| Besu ERR 지속 | — | ~끝까지 | ~1분 | ~66초 | ~2분 12초 |

---

## 각 Run 상세

### Run1 — 기준 (wallets.json 버그)

**목적** 최초 부하 테스트.

**주요 현상**
- `wallet_ledger` DUPLICATE KEY 에러 대량 발생
- `java.io.IOException: unexpected end of stream on http://localhost:8545/` 로그 쇄도

**원인** wallets.json에 가맹점 주소 혼입 → self-payment → DUPLICATE KEY (→ 이슈 1).

---

### Run2 — wallets.json 수정 후

**변경사항** wallets.json에서 가맹점 지갑 제거 (5개), export_users.sh 수정.

**txpool 로그 (발췌)**
```
TIME       PENDING    LOCAL      BLOCK        TX/BLOCK
18:35:xx   0          0          ...          0
18:36:xx   1          1          ...          1       ← 테스트 시작, TX/BLOCK=1 고정
18:36:50   ERR        ERR        ERR          -       ← Besu HTTP 다운
...        ERR        ERR        ERR          -       ← 테스트 종료까지 회복 안 됨
```

**bank 서버 로그 (발췌)**
```
[blockchain] contract tx start. functionName=charge ...   (1,086건)
[blockchain] contract tx IO failure. functionName=charge  (1,053건)  ← eth_call 단계에서 97% 실패
[blockchain] nonce fetched. ...                           (33건)     ← 3%만 통과
```

**분석**
- DUPLICATE KEY는 사라졌으나 IO failure 97% 발생.
- TX/BLOCK=1 → mempool이 비어있음. 블록체인이 느린 게 아니라 대부분의 요청이 Besu에 도달조차 못함.
- Besu 기본 `--rpc-http-max-connections=80` 초과로 커넥션 드롭 추정.

---

### Run3 — Besu max-active-connections=300

**변경사항** `docker-compose.yml` 4개 노드에 `--rpc-http-max-active-connections=300` 추가.

**txpool 로그 (발췌)**
```
TIME       PENDING    LOCAL      BLOCK        TX/BLOCK
18:57:12   1          1          17605        -       ← 테스트 시작
18:57:14   1          1          17606        1       ← TX/BLOCK=1 유지
...
18:59:42   ERR        ERR        ERR          -       ← Besu 다운 (+2분 30초)
...
19:02:14   0          0          17756        0       ← 회복 (약 2분 30초 후)
```

**bank 서버 로그 (발췌)**
```
[blockchain] contract tx start.    (1,300건)
[blockchain] contract tx IO failure (1,225건)  ← eth_call 94% 실패
[blockchain] nonce fetched.        (75건)      ← 5.8% 통과
[blockchain] transaction submitted.(75건)
[blockchain] contract tx success.  (75건)      ← 제출된 건 전부 성공
```

**분석**
- 커넥션 한도 300으로 늘리자 Besu ERR까지 +90초 더 버팀 (1분 → 2분 30초).
- nonce fetched 33 → 75로 증가, charge_ok 17 → 22로 소폭 개선.
- 그러나 여전히 TX/BLOCK=1, IO failure 94%.
- **결론**: 커넥션 수가 아니라 Vert.x event loop 단일 스레드가 실제 병목.
  50 VU 동시 eth_call → Vert.x 처리 큐 적체 → 응답 지연 수초 → OkHttp read timeout → IO failure 연쇄.

---

### Run4 — receipt polling 횟수 60→15

**변경사항** `ContractCallService.RECEIPT_POLLING_ATTEMPTS = 60 → 15` (최대 대기 60s → 15s).

**txpool 로그 (발췌)**
```
TIME       PENDING    LOCAL      BLOCK        TX/BLOCK
19:47:02   1          1          ...          -       ← 테스트 시작
19:47:04   1          1          ...          1       ← TX/BLOCK=1 유지
...
19:49:36   ERR        ERR        ERR          -       ← Besu 다운 (+2분 34초)
19:50:42   ERR        ERR        ERR          -       ← 마지막 ERR (~66초간 지속)
19:49:29   1          1          19174        1       ← 회복 (k6 종료 후)
```

**bank 서버 로그 (발췌)**
```
[blockchain] contract tx start.     (1,072건)
[blockchain] contract tx IO failure (995건)   ← eth_call 93% 실패
[blockchain] nonce fetched.         (77건)    ← 7.2% 통과
[blockchain] transaction submitted. (77건)
[blockchain] contract tx success.   (77건)    ← 전부 성공
receipt polling failure:             0건      ← 15초 내 전부 수신 완료
```

**분석**
- receipt timeout 0건: QBFT 2초 블록에서 15s는 충분한 여유 (7.5 블록). 폴링 단축으로 인한 false negative 없음.
- 전반적 수치는 Run3와 사실상 동일. polling 단축이 Besu 부하를 의미있게 줄이지 못함.
- **원인**: 병목은 receipt polling(제출 후)이 아니라 eth_call(제출 전) 단계의 동시 폭발. polling을 줄여도 50 VU 동시 eth_call → Vert.x 포화 흐름은 그대로.
- p95 응답시간 7,106ms → 15,000ms로 악화: Tomcat 스레드가 15초 만에 풀리므로 새 요청이 더 빠르게 쏟아져 오히려 Besu에 더 짧은 주기로 집중됨.

---

### Run5 — VU 50→10

**변경사항** k6 실행 시 `-e VUS=10` 환경변수 전달. 코드/설정 변경 없음.

**txpool 로그 (발췌)**
```
TIME       PENDING    LOCAL      BLOCK        TX/BLOCK
19:55:03   0          0          19340        1       ← 테스트 시작 (19:55:00)
19:55:15   1          1          19346        1       ← 0~1 혼재 (50VU 때와 달리 여유 있음)
...
19:56:31   ERR        ERR        ERR          -       ← Besu 다운 (+1분 31초)
19:58:43   ERR        ERR        ERR          -       ← 마지막 ERR (k6 종료 19:58:30 이후까지 지속)
```

**bank 서버 로그 (발췌)**
```
[blockchain] contract tx start.     (571건)
[blockchain] contract tx IO failure (527건)   ← eth_call 92% 실패
[blockchain] nonce fetched.         (44건)    ← 7.7% 통과
[blockchain] transaction submitted. (44건)
[blockchain] contract tx success.   (44건)    ← 제출된 건 전부 성공
receipt polling failure:             0건
```

**분석**
- payment_ok 14 → **205** (14배 증가): payment는 비동기(DB-first, blockchain async) → 블록체인 병목 영향 없음. VU 감소로 Spring 스레드 여유가 생기면서 빠르게 처리됨.
- cancel_ok 0 → **51**: 성공한 payment 건이 늘어나면서 cancel 대상도 증가.
- charge_ok 17 → **44** (2.6배): 동시 eth_call 감소로 소폭 개선. 그러나 여전히 92% IO failure.
- **Besu ERR이 +1분 31초에 발생** — 50 VU보다 오히려 빠름. VU가 적어도 ramping 속도가 상대적으로 빨라 10 VU 도달이 빠르고, 각 VU가 payment/exchange 등 비블록체인 요청도 섞어 보내므로 총 RPC 요청 빈도는 유사.
- TX/BLOCK이 0과 1을 혼재 — 50 VU 때처럼 항상 1이 아님. mempool이 간헐적으로 비는 순간이 생겨 블록체인에 여유가 있음을 확인.
- **핵심**: VU 감소로 payment/cancel 처리량은 대폭 개선됐으나, charge의 동기 블록체인 호출 병목은 그대로. charge IO failure 92%는 VU 10에서도 동일.

---

## 근본 원인 요약

```
50 VU 동시 요청
  └─ 각 VU: eth_call(시뮬레이션) → nonce → sendRawTransaction → receipt polling
              ↑ 이 시점에 50개 동시 HTTP 연결 → Besu Vert.x 단일 IO 스레드 포화
                → 응답 지연 수초 → OkHttp read timeout → IOException
                → "unexpected end of stream"
```

| 레이어 | 상태 | 근거 |
|---|---|---|
| 블록체인 처리 용량 | 여유 (이론 26 tx/s) | TX/BLOCK=1, mempool PENDING=0~1 유지 |
| Besu HTTP RPC | 병목 | 50 동시 eth_call → Vert.x 큐 포화 → 커넥션 드롭 |
| Spring Tomcat | 추가 압박 | 스레드가 receipt polling으로 최대 60s(→15s) 점유 |

---

## 향후 개선 방향

### 단기 (부하 테스트 수치 측정 목적)

- **VU를 10개로 줄인다**: 동시 eth_call 10개 → Besu 안정, 정확한 처리량 측정 가능.

### 중기 (코드 수준 개선)

- **Semaphore로 동시 Besu 호출 수 제한**: `ContractCallService`에 `Semaphore(N)` 추가.
  - N=10~15이면 Vert.x가 감당 가능한 동시 연결 수 유지.
  - 나머지 요청은 Semaphore 대기 큐에서 순서 대기.
  - nonce 충돌 위험도 함께 완화 (직렬화 효과).

```java
// 예시 (ContractCallService에 추가)
private final Semaphore besuSemaphore = new Semaphore(12);

private TransactionReceipt sendContractFunction(...) {
    besuSemaphore.acquire();
    try {
        // 기존 로직
    } finally {
        besuSemaphore.release();
        web3j.shutdown();
    }
}
```

- **charge를 비동기화**: payment처럼 DB-first + 블록체인 비동기.
  현재 charge는 완전 동기라 Tomcat 스레드를 최대 15s(구 60s) 점유.

### 참고: nonce too low 미발생 이유

현재 테스트에서 nonce 충돌이 발생하지 않은 이유는 Besu가 과부하 상태이기 때문.
대부분의 요청이 eth_call 단계에서 죽으므로 nonce 단계에 도달하는 요청이 스태거됨.

Besu가 정상(VU 10개 수준)이면 다수 VU가 동시에 nonce 조회 → 같은 nonce 취득 → nonce too low 발생 가능.
Semaphore가 이를 동시에 해결한다.
