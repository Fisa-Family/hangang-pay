# 결제 취소 API — 은행팀 전달 문서

hangang-pay BE가 은행 API에 기대하는 결제 취소 연동 명세입니다.

---

## 1. 취소 요청 (BE → Bank)

### Endpoint

```
POST /api/v1/transactions/cancel
Content-Type: application/json
```

### Request Body

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `transactionUuid` | String (UUID) | ✓ | 이번 CANCEL 거래의 BE 측 식별자. 멱등성 키로 사용한다. |
| `originalTransactionUuid` | String (UUID) | ✓ | 취소 대상인 원본 PAYMENT 거래의 UUID. |
| `fromWalletAddress` | String | ✓ | 취소 송신자 지갑 주소 (가맹점 wallet). 원본 PAYMENT의 `toWalletAddress`와 동일하다. |
| `toWalletAddress` | String | ✓ | 취소 수신자 지갑 주소 (소비자 wallet). 원본 PAYMENT의 `fromWalletAddress`와 동일하다. |
| `amount` | BigDecimal | ✓ | 취소 금액. 원본 PAYMENT 금액과 동일하다. 부분 취소 없음. |

```json
{
  "transactionUuid": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
  "originalTransactionUuid": "11111111-2222-3333-4444-555555555555",
  "fromWalletAddress": "0xMERCHANT_WALLET",
  "toWalletAddress": "0xUSER_WALLET",
  "amount": 15000
}
```

### 취소 방향

원본 PAYMENT는 소비자 → 가맹점 방향입니다. CANCEL은 역방향으로 처리합니다.

```
원본 PAYMENT:  소비자 wallet → 가맹점 wallet
취소 CANCEL:   가맹점 wallet → 소비자 wallet
```

---

## 2. 취소 응답 (Bank → BE)

### 성공 응답 (HTTP 200)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `transactionUuid` | String (UUID) | ✓ | 요청과 동일한 CANCEL UUID. |
| `originalTransactionUuid` | String (UUID) | ✓ | 요청과 동일한 원본 PAYMENT UUID. |
| `bankTransactionId` | Long | ✓ | 은행 내부 거래 식별자. BE가 `String.valueOf()`로 변환해 저장한다. |
| `txHash` | String | ✓ | 블록체인 트랜잭션 해시. |
| `blockNumber` | Long | - | 블록 번호. 저장하지 않으나 로깅에 활용한다. |
| `confirmedAt` | LocalDateTime (ISO-8601) | ✓ | 스마트컨트랙트 반영 완료 시각. BE가 응답 `confirmedAt` 필드로 그대로 전달한다. |
| `fromBalance` | BigDecimal | - | 취소 후 가맹점 wallet 잔액. |
| `toBalance` | BigDecimal | - | 취소 후 소비자 wallet 잔액. |

```json
{
  "transactionUuid": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
  "originalTransactionUuid": "11111111-2222-3333-4444-555555555555",
  "bankTransactionId": 7891234,
  "txHash": "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
  "blockNumber": 1048576,
  "confirmedAt": "2026-05-27T14:00:00",
  "fromBalance": 85000,
  "toBalance": 30000
}
```

> **전제**: 응답이 반환되는 시점은 스마트컨트랙트 반영이 완료된 이후입니다. BE는 응답을 받으면 바로 SUCCESS로 확정합니다.

---

## 3. 거래 상태 조회 (복구용)

### 용도

Bank 호출 중 네트워크 타임아웃이 발생하면 BE는 CANCEL 거래를 `UNKNOWN` 상태로 저장합니다. 이후 복구 API(`POST /cancel/recover`) 또는 복구 스케줄러(1분 주기)가 아래 경로로 상태를 재조회합니다.

### 현재 호출 경로

```
GET /api/v1/transactions/{transactionUuid}/payment/status
```

### 요청사항

현재 경로명에 `payment`가 포함되어 있으나, **CANCEL UUID로도 조회가 가능해야 합니다.** 내부적으로 UUID 기준 공통 조회라면 경로명과 무관하게 동작할 수 있습니다. 단, 향후 명칭 혼선을 줄이기 위해 아래 경로로 정리를 요청드립니다.

```
GET /api/v1/transactions/{transactionUuid}/status   (권장)
```

기존 `payment/status` 경로와의 하위 호환이 필요하다면 두 경로 모두 지원하거나 redirect 처리 부탁드립니다.

### 조회 응답

| 필드 | 타입 | 설명 |
|------|------|------|
| `transactionUuid` | String | 조회한 UUID. |
| `bankTransactionId` | Long | 은행 내부 거래 식별자. 아직 미확정이면 null 가능. |
| `status` | String | `SUCCESS` / `FAILED` / `PROCESSING` / `UNKNOWN` |
| `txHash` | String | 블록체인 해시. SUCCESS일 때만 값 있음. |
| `confirmedAt` | LocalDateTime | 완료 시각. SUCCESS일 때만 값 있음. |

BE 복구 로직 동작:

| Bank 응답 status | BE 처리 |
|-----------------|---------|
| `SUCCESS` | CANCEL 거래를 SUCCESS로 확정, txHash / bankTransactionId 저장 |
| `FAILED` | CANCEL 거래를 FAILED로 확정 |
| `PROCESSING` / `UNKNOWN` | 상태 유지, 다음 스케줄에서 재시도 |

---

## 4. 타임아웃 및 오류 처리

### BE 타임아웃 정책

| 상황 | BE 처리 |
|------|---------|
| 네트워크 단절 / `ResourceAccessException` | CANCEL → `UNKNOWN` 저장, 복구 스케줄러 대기 |
| Bank 4xx (검증 실패 등) | CANCEL → `FAILED` 확정 |
| Bank 5xx | `UNKNOWN` 저장 또는 예외 전파 (기존 BankClient 정책 따름) |

### 복구 스케줄러

- 주기: 1분 (`0 */1 * * * *`)
- 대상: `status = UNKNOWN`, `transactionType = CANCEL`
- 동작: 각 건에 대해 `getTransactionStatus` 호출 → 상태 반영
- 실패 시: WARN 로그 후 다음 주기에 재시도. 스케줄러 전체를 중단하지 않는다.

---

## 5. 멱등성 요구사항

BE는 같은 `transactionUuid`에 대한 취소 요청이 중복 도달하지 않도록 Redis 분산 락 + 멱등성 스토어로 보호합니다.

그러나 네트워크 재시도 등으로 **은행 서버에 동일 `transactionUuid`의 취소 요청이 두 번 도달할 수 있습니다.**

은행 서버도 `transactionUuid` 기준으로 취소 요청을 멱등하게 처리해 주시기 바랍니다.

- 동일 `transactionUuid`로 이미 처리된 취소가 있으면 기존 결과를 그대로 반환한다.
- 중복 처리(double transfer)가 발생하지 않아야 한다.

---

## 6. 요약 — BE가 사용하는 Bank API 경로

| 용도 | 메서드 | 경로 |
|------|--------|------|
| 결제 취소 실행 | POST | `/api/v1/transactions/cancel` |
| 거래 상태 조회 (복구용) | GET | `/api/v1/transactions/{uuid}/payment/status` (현재) → `/api/v1/transactions/{uuid}/status` (요청) |
