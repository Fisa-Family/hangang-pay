# Payment Cancel API Phase Plan

## Context

hangang-pay BE에 가맹점 결제 취소 API를 구현한다.

취소는 원본 PAYMENT 거래를 수정하지 않고 `TransactionType.CANCEL` 신규 레코드로 남긴다. 원본 PAYMENT는 `SUCCESS` 상태로 유지하고, 취소 거래가 `PENDING -> PROCESSING -> SUCCESS / FAILED / UNKNOWN` 상태 머신을 따른다.

이 문서는 세션 초기화 후에도 이어서 작업할 수 있도록 현재 목표, 제외 범위, phase별 RED/GREEN 상태, 다음 시작 지점을 보존하는 handoff 문서다.

## Confirmed Decisions

- API path: `POST /api/v1/merchant/payments/{transactionId}/cancel`
- `transactionId`: `transaction.id` Long 값이다.
- 요청 주체: MERCHANT 세션의 `partyId`.
- PIN 검증 주체: 가맹점. `Merchant.matchesPaymentPin()`을 사용한다.
- CANCEL 방향:
  - 원본 PAYMENT: user wallet -> merchant wallet
  - 취소 CANCEL: merchant wallet -> user wallet
  - `fromParty` = merchant, `toParty` = user
  - `fromWallet` = original `toWallet`, `toWallet` = original `fromWallet`
- 승인번호: `APV-{YYYY}-{transaction.id:08d}`
- `CancelResponse.bankTransactionId`: `Long`으로 수신하고 `String.valueOf()`로 저장한다.
- Bank 응답은 스마트컨트랙트 반영 완료 후 반환된다는 전제다.
- `UNKNOWN` 복구는 API와 스케줄러를 모두 둔다.
- 은행팀 전달용 `cancel.md`는 최종 phase 산출물이다.

## Scope Policy

### Early Phase Scope

초반 phase는 기본 취소 성공 흐름을 먼저 완성한다.

- 원본 PAYMENT SUCCESS 조회
- 가맹점 소유권 검증
- 가맹점 paymentPin 검증
- SUCCESS CANCEL 중복 차단
- CANCEL 거래 생성
- Bank cancel 호출
- SUCCESS 반영
- REST 문서 갱신

### Explicit Follow-up Scope

아래 항목은 최종 목표에 포함되지만, 기본 취소 API 이후 별도 phase에서 구현한다.

- Bank timeout / 5xx / network error 시 CANCEL `UNKNOWN` 저장
- CANCEL `UNKNOWN` 복구 API
- CANCEL `UNKNOWN` 복구 스케줄러
- Redis lock 기반 중복 취소 방지
- 취소 멱등성 저장소
- 은행팀 전달용 `cancel.md`

## Transaction Boundary

`BankClient.cancel()`은 DB 트랜잭션 밖에서 호출한다.

권장 구조:

```text
MerchantController
-> TransactionCommandService.cancelPayment()
   - @Transactional(propagation = NOT_SUPPORTED)
   - 전체 orchestration 담당

-> CancelExecutionStateWriter.prepareCancel()
   - @Transactional(propagation = REQUIRES_NEW)
   - 원본 검증, CANCEL 저장, 승인번호 생성, PROCESSING 전환

-> BankClient.cancel()
   - DB 트랜잭션 밖에서 호출

-> CancelExecutionStateWriter.completeSuccess()
   - @Transactional(propagation = REQUIRES_NEW)
   - SUCCESS, txHash, bankTransactionId 반영

-> CancelExecutionStateWriter.markUnknown()
   - 후속 UNKNOWN phase에서 추가
```

주의:

- private 메서드에 `@Transactional`을 붙여 트랜잭션 분리를 기대하지 않는다.
- 기존 PAYMENT 실행 흐름은 초반 phase에서 수정하지 않는다.

## Phase 0: Intake

### Goal

`POST /api/v1/merchant/payments/{transactionId}/cancel` 가맹점 결제 취소 기본 흐름을 구현한다.

### DoD

- `transactionId`로 원본 `PAYMENT + SUCCESS` 건을 조회한다.
- session `merchantPartyId`가 원본 PAYMENT의 `toParty`인지 검증한다.
- 가맹점 paymentPin을 검증한다.
- 이미 `CANCEL + SUCCESS`가 존재하면 재취소를 막는다.
- CANCEL 트랜잭션 저장 -> 승인번호 생성 -> `BankClient.cancel()` 호출 -> SUCCESS 확정.
- 응답은 `PaymentCancelResponse`로 반환한다.

### Forbidden

- Phase 0에서는 Redis 기반 분산 락을 추가하지 않는다.
- Phase 0에서는 취소 멱등성 스토어를 추가하지 않는다.
- Phase 0에서는 UNKNOWN 복구 API와 스케줄러를 구현하지 않는다.
- Phase 0에서는 기존 PAYMENT 실행 흐름을 수정하지 않는다.

### Current Handoff State

- `CancelResponse.bankTransactionId` 필드가 추가되어 있는지 확인한다.
- `docs/rest_api.md`의 MERCHANT-003/004 path가 `{transactionId}` 기준인지 확인한다.
- 기존 `cancel-phase.md`는 이 파일이 live handoff 문서다.

## Phase 1: RED Tests - Basic Cancel

### Goal

`TransactionCommandServiceTest`에 기본 취소 시나리오 실패 테스트를 먼저 추가한다.

### Test Cases

- `cancelPayment_success`
  - `PAYMENT + SUCCESS` 원본
  - merchant ownership OK
  - merchant PIN OK
  - CANCEL 생성
  - `BankClient.cancel()` 호출
  - CANCEL SUCCESS 확정
- `cancelPayment_failsWhenMerchantIsNotReceiver`
  - session merchantPartyId가 원본 PAYMENT `toParty`가 아니면 예외
- `cancelPayment_failsWhenPaymentNotSuccess`
  - 원본 PAYMENT status가 `SUCCESS`가 아니면 예외
- `cancelPayment_failsWhenAlreadyCancelled`
  - 동일 원본에 `CANCEL + SUCCESS`가 있으면 예외

### RED Command

```bash
./gradlew test --tests family.fisa.hangangpay.domain.transaction.service.TransactionCommandServiceTest
```

### Expected RED State

- `TransactionCommandService.cancelPayment(Long merchantPartyId, Long transactionId, ...)` 미구현으로 컴파일 실패 또는 테스트 실패.
- 신규 DTO, ErrorCode, Repository 메서드, Entity 메서드는 아직 없다.

## Phase 2: Basic Cancel API

### Goal

기본 취소 성공 흐름을 GREEN으로 만든다.

### Files

- `domain/transaction/dto/request/PaymentCancelRequest.java`
  - `paymentPin`
- `domain/transaction/dto/response/PaymentCancelResponse.java`
  - `transactionUuid`
  - `approvalNumber`
  - `txHash`
  - `amount`
  - `confirmedAt`
- `domain/transaction/code/TransactionErrorCode.java`
  - `PAYMENT_ALREADY_CANCELLED` (409)
  - `PAYMENT_NOT_CANCELLABLE` (400)
- `domain/transaction/entity/Transaction.java`
  - `validateMerchantIsReceiver(Long merchantPartyId)`
  - `validateCancellable()`
  - `assignApprovalNumber(String approvalNumber)`
- `domain/transaction/repository/TransactionRepository.java`
  - `boolean existsSuccessCancelFor(String originalTransactionUuid)`
- `domain/transaction/repository/TransactionRepositoryImpl.java`
  - 위 메서드 구현
- `domain/transaction/repository/TransactionJpaRepository.java`
  - 위 메서드에 필요한 query 추가
- `domain/transaction/service/CancelExecutionStateWriter.java`
  - CANCEL 저장, PROCESSING 전환, SUCCESS 반영 담당
- `domain/transaction/service/TransactionCommandService.java`
  - `cancelPayment(...)` orchestration 추가
- `domain/merchant/controller/MerchantController.java`
  - `POST /api/v1/merchant/payments/{transactionId}/cancel`

### Expected Flow

```text
1. findDetailByIdAndTypes(transactionId, [PAYMENT])
2. original.validateMerchantIsReceiver(merchantPartyId)
3. original.validateCancellable()
4. merchant paymentPin 검증
5. existsSuccessCancelFor(original.transactionUuid)
   - true면 PAYMENT_ALREADY_CANCELLED
6. cancelUuid 생성
7. Transaction.forCancel(
       cancelUuid,
       original.transactionUuid,
       merchantParty,
       userParty,
       merchantWallet,
       userWallet,
       amount,
       null
   )
8. transactionRepository.save(cancelTx)
9. cancelTx.assignApprovalNumber("APV-" + year + "-" + id 8자리)
10. cancelTx.markProcessing()
11. DB 트랜잭션 종료
12. BankClient.cancel(CancelRequest)
13. cancelTx.completeWithBankResponse(txHash, String.valueOf(bankTransactionId))
14. return PaymentCancelResponse.from(cancelTx, bankResponse.confirmedAt())
```

### GREEN Commands

```bash
./gradlew test --tests family.fisa.hangangpay.domain.transaction.service.TransactionCommandServiceTest
./gradlew test
```

## Phase 3: UNKNOWN Handling

### Goal

Bank cancel 호출 결과를 확정할 수 없는 경우 CANCEL을 `UNKNOWN`으로 저장한다.

### Rules

- `ResourceAccessException`, timeout, network error는 실패 확정이 아니다.
- 은행에 요청이 도달했을 수 있으므로 `FAILED`로 저장하지 않는다.
- CANCEL 거래를 `UNKNOWN`으로 저장하고 응답도 `UNKNOWN` 상태를 표현한다.
- 4xx 검증 오류처럼 은행이 명확히 거부한 케이스는 별도 정책이 필요하다. 이 phase에서는 기존 `BankClient` 예외 정책을 따른다.

### Test Cases

- `cancelPayment_marksUnknownWhenBankTimeout`
  - Bank timeout 발생
  - CANCEL status = `UNKNOWN`
  - txHash 없음
  - 실패 확정 아님

### Verification

```bash
./gradlew test --tests family.fisa.hangangpay.domain.transaction.service.TransactionCommandServiceTest
```

## Phase 4: Cancel Recovery API

### Goal

사용자가 취소 결과를 기다리거나 재시도할 때 `UNKNOWN` CANCEL을 은행 상태 조회로 복구할 수 있게 한다.

### API

```text
POST /api/v1/merchant/payments/{transactionId}/cancel/recover
```

`transactionId`는 원본 PAYMENT의 `transaction.id`다.

### Repository Needs

- `Optional<Transaction> findRecoverableCancelByOriginalTransactionUuid(String originalTransactionUuid)`
  - `transactionType = CANCEL`
  - `status in (UNKNOWN, PROCESSING)`

### Flow

```text
1. 원본 PAYMENT 조회
2. merchantPartyId가 원본 PAYMENT.toParty인지 검증
3. 원본 transactionUuid로 recoverable CANCEL 조회
4. 없으면 PAYMENT_NOT_CANCELLABLE 또는 별도 CANCEL_NOT_RECOVERABLE 예외
5. bankClient.getTransactionStatus(cancelTx.transactionUuid)
6. Bank SUCCESS -> cancelTx.recoverSuccess(txHash, bankTransactionId)
7. Bank FAILED -> cancelTx.recoverFailed()
8. Bank PROCESSING/UNKNOWN -> cancelTx UNKNOWN 유지
9. PaymentCancelResponse 반환
```

### Test Cases

- `recoverCancel_successFromUnknown`
- `recoverCancel_failedFromUnknown`
- `recoverCancel_keepsUnknownWhenBankStillProcessing`
- `recoverCancel_failsWhenNoRecoverableCancel`
- `recoverCancel_failsWhenMerchantIsNotReceiver`

### Verification

```bash
./gradlew test --tests family.fisa.hangangpay.domain.transaction.service.TransactionCommandServiceTest
./gradlew test
```

## Phase 5: Cancel Recovery Scheduler

### Goal

사용자가 이탈해도 `CANCEL + UNKNOWN` 거래가 계속 남지 않도록 자동 복구한다.

### Design

기존 `UnknownPaymentRecoveryScheduler`를 확장하거나 이름을 `UnknownTransactionRecoveryScheduler`로 바꾼다.

스케줄러는 1개 클래스로 유지하고, 타입별 `@Scheduled` 메서드를 분리한다.

```text
resolveUnknownPayments()
-> PAYMENT UNKNOWN 복구

resolveUnknownCancels()
-> CANCEL UNKNOWN 복구
```

### Repository Needs

- `List<Transaction> findAllUnknownByType(TransactionType type)`

### Scheduler Flow

```text
1. findAllUnknownByType(TransactionType.CANCEL)
2. 각 CANCEL의 originalTransactionUuid로 원본 PAYMENT 조회 또는 기존 relation 정보 사용
3. merchantPartyId = cancelTx.fromParty.id
4. transactionCommandService.recoverCancel(merchantPartyId, originalPaymentId or originalTransactionUuid)
5. 실패하면 WARN 로그 후 다음 스케줄에서 재시도
```

### Logging

- INFO: 스케줄러 시작, 대상 건수, 복구 성공
- WARN: 개별 복구 실패와 reason
- ERROR: 스케줄러 자체가 중단될 수준의 예외

### Verification

```bash
./gradlew test --tests family.fisa.hangangpay.domain.transaction.service.TransactionCommandServiceTest
./gradlew test
```

## Phase 6: Redis Lock and Idempotency

### Goal

동시 취소 요청과 같은 취소 요청 반복을 안전하게 처리한다.

### Lock

중복 취소 방지는 원본 PAYMENT 기준으로 잠근다.

```text
lock key = cancel:lock:{originalPaymentTransactionUuid}
```

이유:

- cancel UUID는 CANCEL 생성 전에는 존재하지 않는다.
- 같은 원본 PAYMENT에 대해 CANCEL이 2개 생성되는 것을 막아야 한다.

### Idempotency

취소 멱등성은 원본 PAYMENT UUID와 요청 fingerprint를 기준으로 판단한다.

권장 key:

```text
cancel:idempotency:{originalPaymentTransactionUuid}
```

처리 규칙:

- 같은 key + 같은 requestHash + SUCCESS snapshot 있음 -> 기존 응답 반환
- 같은 key + 같은 requestHash + PROCESSING/UNKNOWN -> 현재 상태 반환 또는 already processing
- 같은 key + 다른 requestHash -> `IDEMPOTENCY_CONFLICT`
- FAILED CANCEL만 있는 경우 재취소 허용 여부는 정책에 따른다. 현재 계획은 FAILED가 있으면 재취소 허용이다.

### Refactor Options

1. Cancel 전용 헬퍼 추가
   - `CancelIdempotencyStore`
   - `CancelLockManager`
   - `CancelRateLimiter`
2. Transaction 공통 헬퍼로 리네이밍
   - `Payment*` -> `Transaction*`
   - 기존 PAYMENT 테스트 전체 회귀 검증 필수

현재 권장:

- 기본 API 안정화 전에는 Cancel 전용 헬퍼 추가가 더 작다.
- 공통화는 별도 리팩터링 phase로 분리한다.

### Verification

```bash
./gradlew test --tests '*Redis*'
./gradlew test
```

## Phase 7: Docs and Bank Handoff

### Goal

BE 문서와 은행팀 전달 문서를 최종 정리한다.

### BE Docs

- `docs/rest_api.md`
  - MERCHANT-004 취소 API 요청/응답 상세
  - 취소 복구 API 추가
- `docs/erd.md`
  - `CANCEL`, `originalTransactionUuid`, `approvalNumber`, `UNKNOWN` 상태가 현재 문서와 맞는지 확인
- `docs/package.md`
  - 결제 취소 API는 merchant controller에 노출하고 transaction domain에서 관리한다는 기존 규칙 유지

### Bank Handoff: `cancel.md`

루트 `cancel.md`에 은행팀 전달 내용을 작성한다.

필수 포함:

- BE -> Bank cancel request
  - `transactionUuid`: CANCEL 거래 UUID
  - `originalTransactionUuid`: 원본 PAYMENT UUID
  - `fromWalletAddress`: merchant wallet
  - `toWalletAddress`: user wallet
  - `amount`
- Bank -> BE cancel response
  - `transactionUuid`
  - `originalTransactionUuid`
  - `bankTransactionId`
  - `txHash`
  - `confirmedAt`
  - balance fields if provided
- Status 조회 요구사항
  - CANCEL UUID로도 `getTransactionStatus(uuid)`가 조회되어야 한다.
  - 현재 path 이름이 payment/status여도 구현상 UUID 기반 공통 조회가 보장되어야 한다.
  - 가능하면 은행팀에서 추후 common status path로 명칭 정리한다.
- Timeout 처리 방식
  - BE는 timeout/network error를 `UNKNOWN`으로 저장한다.
  - 복구 API와 스케줄러가 Bank status를 재조회한다.
- Idempotency 기대사항
  - 은행도 같은 `transactionUuid`에 대한 cancel 중복 호출을 멱등하게 처리해야 한다.

### Final Verification

```bash
./gradlew spotlessApply
./gradlew test
```

## Phase Checklist

- [x] Phase 0 Intake complete
- [x] Phase 1 RED tests added and RED confirmed
- [x] Phase 2 Basic cancel API GREEN
- [x] Phase 3 UNKNOWN handling GREEN
- [x] Phase 4 Cancel recovery API GREEN
- [x] Phase 5 Cancel recovery scheduler GREEN
- [x] Phase 6 Redis lock/idempotency GREEN
- [ ] Phase 7 docs and `cancel.md` complete

## Current Next Action

Start at Phase 7: Docs and Bank Handoff.

- `docs/rest_api.md` — MERCHANT-004 취소 API, MERCHANT-004-R 복구 API 요청/응답 상세 갱신
- `docs/erd.md` — CANCEL type, originalTransactionUuid, approvalNumber, UNKNOWN 상태 반영 확인
- `cancel.md` (루트) — 은행팀 전달 완료

## Phase 6 Handoff Notes

- `internal/cancel/` 하위에 `CancelLockManager`, `CancelIdempotencyStore`, `CancelIdempotencyDecision`, `CancelIdempotencyDecisionType` 인터페이스/타입 구현 완료.
- `infra/redis/cancel/` 하위에 `RedisCancelLockManager`, `RedisCancelIdempotencyStore`, `CancelIdempotencyRecord` 구현 완료.
- lock key: `cancel:lock:{originalPaymentUuid}`, idempotency key: `cancel:idempotency:{originalPaymentUuid}`.
- `TransactionErrorCode`에 `CANCEL_ALREADY_PROCESSING`, `CANCEL_IDEMPOTENCY_RECORD_NOT_FOUND`, `CANCEL_IDEMPOTENCY_RECORD_INVALID` 추가 완료.
- `TransactionCommandService.cancelPayment()`에 lock + idempotency 흐름 통합 필요 (아직 미연결).
- `CancelIdempotencyRecord`는 `status` 필드를 포함한다. `beginCancel`의 판단 로직은 `responseSnapshot` 유무만 본다. `status`는 Redis 직접 조회 시 현재 상태 파악용(observability)이다.

## Phase 3 Handoff Notes

- `markUnknown`은 `findByTransactionUuid(cancelUuid)`로 재조회한다. 단순히 `CancelExecutionPrepared`에 `id`가 없어서가 아니라, UUID가 은행과 공유하는 식별자이기 때문이다. Phase 4 복구에서 `bankClient.getTransactionStatus(cancelUuid)`를 호출할 때도 동일한 UUID를 쓴다.
- `PaymentCancelResponse`에 `status` 필드가 추가됐다. Phase 4 복구 응답도 동일 DTO를 재사용하면 된다.
- `TransactionCommandService`에서 `UserRepository` 의존성이 제거됐다. 테스트 생성자와 실제 생성자를 맞출 것.

## Assumptions

- `transactionId` means `transaction.id`, not approval number and not transaction UUID.
- CANCEL FAILED records do not block retry; only SUCCESS blocks re-cancel in the basic phase.
- Redis lock/idempotency is intentionally deferred to Phase 6.
- UNKNOWN recovery API and scheduler are both required because API supports active user retry and scheduler covers user abandonment.
- Existing user PAYMENT execution flow should not be changed while implementing basic cancel.
