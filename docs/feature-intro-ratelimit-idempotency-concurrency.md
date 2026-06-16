# 3-3. 세부 기능 소개

> 코드 링크는 직접 추가. 각 항목에 클래스·메소드명을 명시해 두었으니 해당 위치로 링크를 걸면 됩니다.
> 기능 설명은 초안 상태이며 이후 `/humanizer`로 다듬습니다.

---

## c. Rate Limit (요청 속도 제한)

### 기능 설명

결제 도메인의 요청 폭주를 막기 위해 Redis 기반 속도 제한을 적용한다. 결제는 단계마다 위험도와 호출 성격이 달라서 4개 구간으로 나눠 서로 다른 알고리즘을 쓴다.

- **결제 의도 생성(Intent)** 과 **은행 아웃바운드(BankOutbound)** 는 **토큰 버킷(Token Bucket)** 을 쓴다. 평소에는 버스트를 허용하되 시간이 지나면 토큰이 일정 비율로 다시 차오른다. Intent는 사용자별 버킷, BankOutbound는 모든 결제 실행·복구가 공유하는 전역 버킷이라 은행으로 나가는 전체 트래픽의 천장 역할을 한다.
- **결제 실행(Execute)** 과 **결제 복구(Recovery)** 는 **슬라이딩 윈도우(Sliding Window)** 를 쓴다. 최근 N분/N초 안의 요청 개수를 정확히 세어 한도를 넘으면 막는다.

판정 로직은 두 종류의 Lua 스크립트로 Redis에서 **원자적으로** 실행된다. 토큰 재충전·차감, 윈도우 정리·카운트가 한 번의 스크립트 안에서 끝나므로 동시에 들어온 요청들이 경쟁 상태(race condition) 없이 정확히 카운트된다. 스크립트가 `1`을 반환하면 통과, `0`이면 `PAYMENT_RATE_LIMIT_EXCEEDED`(HTTP 429) 예외를 던진다. 한도·충전율은 `application.yaml`로 외부화해 두어 부하 테스트 시 `enabled: false`로 우회하거나 천장을 조정할 수 있다.

| 구간 | 알고리즘 | Redis 키 | 기본 한도 |
|------|----------|----------|-----------|
| Intent | Token Bucket | `payment:rate:intent:{partyId}` | 용량 10, 분당 1개 충전 |
| Execute | Sliding Window | `payment:rate:execute:{partyId}` | 10분 내 5회 |
| Recovery | Sliding Window | `payment:rate:recovery:{partyId}` | 60초 내 3회 |
| BankOutbound | Token Bucket (전역) | `payment:rate:bank-outbound` | 용량 50, 초당 10개 충전 |

**호출 지점**: `TransactionCommandService.createPaymentIntent` → `checkIntentRateLimit`, `PaymentExecutionStateWriter.prepareExecution` → `checkExecutionRateLimit` + `checkBankOutboundRateLimit`, `PaymentExecutionStateWriter.prepareRecovery` → `checkRecoveryRateLimit` + `checkBankOutboundRateLimit`.

### 핵심 코드

```java
// PaymentRateLimiter — 구간별 검사 진입점 (포트 인터페이스)
public interface PaymentRateLimiter {
    void checkIntentRateLimit(Long partyId, Long merchantPartyId);              // 토큰 버킷
    void checkExecutionRateLimit(Long partyId, Long merchantPartyId, String transactionUuid); // 슬라이딩 윈도우
    void checkRecoveryRateLimit(Long partyId, String transactionUuid);          // 슬라이딩 윈도우
    void checkBankOutboundRateLimit();                                          // 전역 토큰 버킷
}
```

```lua
-- 토큰 버킷 (Intent, BankOutbound) : 경과 시간만큼 충전 후 1개 소비
-- ARGV: [1]현재시각ms [2]용량 [3]ms당 충전율 [4]TTL ms
local now_ms = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local refill_rate = tonumber(ARGV[3])
local ttl_ms = tonumber(ARGV[4])

local tokens = tonumber(redis.call('hget', KEYS[1], 'tokens')) or capacity
local last_refill_ms = tonumber(redis.call('hget', KEYS[1], 'last_refill_ms')) or now_ms

-- 1. 마지막 충전 이후 경과 시간만큼 토큰을 채우되 용량을 넘지 않게 한다
local elapsed_ms = math.max(0, now_ms - last_refill_ms)
tokens = math.min(capacity, tokens + (elapsed_ms * refill_rate))

-- 2. 토큰이 1개 이상이면 1개 차감하고 통과(1), 아니면 거절(0)
if tokens >= 1 then
    tokens = tokens - 1
    redis.call('hset', KEYS[1], 'tokens', tokens, 'last_refill_ms', now_ms)
    redis.call('pexpire', KEYS[1], ttl_ms)
    return 1
end
redis.call('hset', KEYS[1], 'tokens', tokens, 'last_refill_ms', now_ms)
redis.call('pexpire', KEYS[1], ttl_ms)
return 0
```

```lua
-- 슬라이딩 윈도우 (Execute, Recovery) : 윈도우 밖 항목 제거 후 개수 비교
-- ARGV: [1]현재시각ms [2]윈도우ms [3]한도 [4]멤버(시각:UUID) [5]TTL ms
local now_ms = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])

-- 1. 윈도우(now - window) 밖으로 벗어난 오래된 요청 기록을 제거한다
redis.call('zremrangebyscore', KEYS[1], 0, now_ms - window_ms)

-- 2. 남은 개수가 한도 미만이면 현재 요청을 추가하고 통과(1), 아니면 거절(0)
local count = redis.call('zcard', KEYS[1])
if count < limit then
    redis.call('zadd', KEYS[1], now_ms, ARGV[4])
    redis.call('pexpire', KEYS[1], tonumber(ARGV[5]))
    return 1
end
redis.call('pexpire', KEYS[1], tonumber(ARGV[5]))
return 0
```

```java
// RedisPaymentRateLimiter — 스크립트 결과가 1이 아니면 429 예외
private void checkTokenBucket(String key, long capacity, double refillRatePerMs, Duration ttl) {
    Long allowed = redisTemplate.execute(TOKEN_BUCKET_SCRIPT, List.of(key), ...);
    if (!Long.valueOf(1L).equals(allowed)) {
        throw new BusinessException(TransactionErrorCode.PAYMENT_RATE_LIMIT_EXCEEDED);
    }
}
```

### 코드 링크

- `PaymentRateLimiter` (인터페이스) :
- `RedisPaymentRateLimiter` (Lua 스크립트 + `checkTokenBucket` / `checkSlidingWindow`) :
- `PaymentRateLimitProperties` (한도 설정) :
- `application.yaml` (`payment.rate-limit`) :

---

## d. 멱등성 (Idempotency)

### 멱등키(idempotency key)란

같은 결제 요청이 네트워크 재시도나 중복 클릭으로 여러 번 들어와도 **딱 한 번만** 처리되도록 보장하는 기준 값이다. 한강페이에서는 결제 의도 생성 시 **서버가 발급한 `transactionUuid`** 가 멱등키 역할을 한다. 클라이언트는 재시도할 때 같은 `transactionUuid`를 그대로 보내고, BE·bank·블록체인 세 계층이 각자 이 키를 기준으로 중복을 걸러낸다. 한 계층이 뚫려도 다음 계층이 다시 막는 다중 방어 구조다.

```
[FE] ─ transactionUuid ─▶ [BE] Redis 멱등 저장소 ─▶ [bank] DB 유니크 제약 ─▶ [BC] 컨트랙트 processedTx
        (멱등키 발급)         (응답 스냅샷 재사용)       (원장 1행 보장)        (온체인 중복 mint/transfer 차단)
```

---

### d-1. BE — Redis 멱등 저장소 + requestHash

#### 기능 설명

BE는 Redis의 `setIfAbsent`(SET NX) 원자성으로 멱등을 처리한다. 결제 실행이 시작되면 `transactionUuid`를 키로 `PROCESSING` 상태 레코드를 선점한다. 동시에 같은 키로 여러 요청이 들어와도 `setIfAbsent`가 성공하는 건 단 하나뿐이라, 그 요청만 신규로 진행하고 나머지는 기존 레코드를 보고 분기한다.

기존 레코드가 있으면 다음 순서로 판정한다.
1. **requestHash 비교** — `transactionUuid`는 같은데 요청 본문(요청자·금액 등)이 다르면 다른 결제를 같은 키로 재사용하려는 충돌이므로 `CONFLICT`로 막는다. requestHash는 `RequestHashDigest`가 만드는 SHA-256 다이제스트다.
2. **응답 스냅샷 존재** — 이미 완료된 요청이면 은행을 다시 부르지 않고 저장해 둔 응답 스냅샷을 그대로 돌려준다(`RETURN_SNAPSHOT`).
3. **FAILED 상태** — 복구가 실패로 확정한 거래면 재시도를 거절한다(`ALREADY_FAILED`).
4. **그 외** — 앞선 요청이 아직 처리 중이므로 `PROCESSING`으로 대기시킨다.

레코드 TTL은 7일이다. payment·charge·cancel·exchange 네 흐름이 같은 패턴을 공유하되, cancel만 멱등키로 **원본 결제의 `transactionUuid`** 를 쓴다(취소 자신의 uuid는 처리 시작 시점에 아직 없기 때문). BE가 bank를 호출할 때는 requestHash를 넘기지 않는다 — requestHash는 어디까지나 BE 내부에서 같은 uuid의 본문 변조를 감지하는 용도이고, bank는 `transactionUuid` 자체로 멱등을 처리한다.

#### 핵심 코드

```java
// RedisPaymentIdempotencyStore.beginExecution — Redis SET NX로 선점 후 상태별 판정
@Override
public PaymentIdempotencyDecision beginExecution(
        String transactionUuid, String requestHash, Long transactionId) {

    String key = key(transactionUuid);

    // 1. PROCESSING 레코드를 원자적으로 선점. 동시 요청 중 하나만 created=true가 된다.
    PaymentIdempotencyRecord newRecord =
            PaymentIdempotencyRecord.processing(transactionUuid, requestHash, transactionId);
    Boolean created =
            redisTemplate.opsForValue().setIfAbsent(key, serialize(newRecord), IDEMPOTENCY_TTL);
    if (Boolean.TRUE.equals(created)) {
        return PaymentIdempotencyDecision.newRequest();   // 신규 → 그대로 진행
    }

    // 2. 이미 키가 있으면 기존 레코드로 판정
    PaymentIdempotencyRecord existing = readRecord(key);

    if (!existing.requestHash().equals(requestHash)) {
        return PaymentIdempotencyDecision.conflict();      // 같은 uuid, 다른 본문 → 충돌
    }
    if (existing.responseSnapshot() != null) {
        return PaymentIdempotencyDecision.returnSnapshot(existing.responseSnapshot()); // 완료 → 응답 재사용
    }
    if (existing.status() == TransactionStatus.FAILED) {
        return PaymentIdempotencyDecision.alreadyFailed(); // 실패 확정 → 재시도 거절
    }
    return PaymentIdempotencyDecision.processing();        // 처리 중 → 대기
}
```

#### 코드 링크

- `PaymentIdempotencyStore` (포트) / `RedisPaymentIdempotencyStore` (`beginExecution` / `completeExecution` / `failExecution`) :
- `PaymentIdempotencyDecision` (`NEW_REQUEST` / `RETURN_SNAPSHOT` / `PROCESSING` / `CONFLICT` / `ALREADY_FAILED`) :
- `RequestHashDigest`, `PaymentRequestHashGenerator`, `CancelRequestHashGenerator` :
- cancel/exchange 변형: `RedisCancelIdempotencyStore.beginCancel`, `RedisExchangeIdempotencyStore.beginExecution` :

---

### d-2. bank — DB 유니크 제약 + 상태머신

#### 기능 설명

은행은 **DB 유니크 제약과 원장(ledger) 상태머신** 조합으로 멱등을 보장한다. BE가 넘긴 `transactionUuid`를 멱등키로 원장의 유니크 컬럼/복합 제약에 박아 두어, 같은 거래는 DB 차원에서 한 행만 존재할 수 있다.

거래 종류별로 원장과 진입 방식이 다르다.
- **환전(`AccountLedger`)**: `findByIdempotentKey`로 기존 원장을 먼저 조회하고, 있으면 상태로 분기한다(`handleIdempotent`).
- **결제/취소(`WalletLedger`)**: `(transaction_uuid, bank_wallet_id)` 복합 유니크로 지갑당 1회만 보장한다. `findExisting`으로 조회 후 분기.
- **충전(`BlockchainLedger`)**: `idempotentKey` 유니크 컬럼에 `PENDING`을 선점(insert)하다가 유니크 위반(`DataIntegrityViolationException`)이 나면 다른 요청이 이미 선점한 것이므로 즉시 중복 처리로 응답한다.

분기 규칙은 세 흐름 공통이다. **SUCCESS** 면 저장된 결과를 그대로 반환하고, **PENDING** 이면 `TRANSACTION_DUPLICATE_PROCESSING`, **FAILED** 면 `TRANSACTION_ALREADY_FAILED` 예외를 던진다. 여기에 더해 비동기 블록체인 동기화 컨슈머(`BlockchainSyncProcessor`)도 처리 전 원장 상태를 확인(`isTerminal`)해 이미 끝난 메시지는 무시하고 txHash를 체크포인트로 남긴다. 덕분에 BE 재시도뿐 아니라 은행 서버 재시작·메시지 재투입으로 인한 중복까지 차단된다.

#### 핵심 코드

```java
// 환전 — 멱등키로 기존 원장 조회 후 상태 분기
Optional<AccountLedger> existing =
        accountLedgerRepository.findByIdempotentKey(request.transactionUuid());
if (existing.isPresent()) {
    return handleIdempotent(request, existing.get());
}

private ExchangeResponse handleIdempotent(ExchangeRequest request, AccountLedger existing) {
    switch (existing.getStatus()) {
        case SUCCESS:                                       // 완료 → 기존 결과 반환
            return ExchangeResponse.from(request.transactionUuid(), existing, ...);
        case PENDING:                                       // 처리 중 → 중복 처리
            throw new BusinessException(TransactionErrorCode.TRANSACTION_DUPLICATE_PROCESSING);
        default:                                            // 실패 → 재시도 거절
            throw new BusinessException(TransactionErrorCode.TRANSACTION_ALREADY_FAILED);
    }
}
```

```java
// 충전 — PENDING 선점 시 유니크 제약 위반이면 중복으로 판정
public Long claimPendingCharge(ChargeRequest request) {
    try {
        BlockchainLedger ledger = blockchainLedgerRepository.save(
                BlockchainLedger.of(institution, BlockchainTxStatus.PENDING, request.transactionUuid()));
        return ledger.getId();
    } catch (DataIntegrityViolationException e) {
        // 유니크 제약(transactionUuid) 위반 = 다른 요청이 이미 선점함
        throw new BusinessException(TransactionErrorCode.TRANSACTION_DUPLICATE_PROCESSING);
    }
}
```

#### 코드 링크

- 환전: `ExchangeExecutionService.exchange` / `handleIdempotent` :
- 결제·취소: `PaymentExecutionService.payment` / `PaymentStateWriter.findExisting` :
- 충전: `ChargeStateWriter.claimPendingCharge` / `getSuccessResponse` :
- 비동기 컨슈머: `BlockchainSyncProcessor.processPayment` / `processCancel` / `processExchange` :
- 원장 엔티티 유니크 제약: `AccountLedger`, `WalletLedger`, `BlockchainLedger` :

---

### d-3. BC — 스마트컨트랙트 `processedTx` 매핑

#### 기능 설명

블록체인 계층에서는 `LocalCurrencyPolicy` 컨트랙트가 `processedTx`(`mapping(bytes32 => bool)`)로 온체인 중복을 막는다. 결제(`pay`)·취소(`cancelPayment`) 함수는 진입하자마자 `transactionUuid`가 이미 처리됐는지 확인하고, 처리된 거래면 `AlreadyProcessed`로 즉시 revert한다. 통과한 경우에만 해당 키를 `true`로 마킹한 뒤 토큰을 이체(`forceTransfer`)한다. 별도 nonce나 requestId 없이 BE가 발급한 `transactionUuid`를 그대로 키로 쓴다.

즉 BE의 Redis 멱등이 실수로 같은 거래를 두 번 호출하더라도, 컨트랙트가 마지막 방어선으로 온체인 잔액의 이중 반영을 막는다. 운영상의 재시도·복구·응답 캐싱은 BE가 담당하고, 컨트랙트는 블록체인 상태의 정합성만 지키는 역할 분담이다.

#### 핵심 코드

```solidity
// LocalCurrencyPolicy.sol — 결제 함수
mapping(bytes32 => bool) public processedTx;   // transactionUuid → 처리 여부

function pay(bytes32 transactionUuid, address from, address to, uint256 amount)
    external onlyOwner returns (bool)
{
    // 1. 이미 처리된 거래면 즉시 revert (온체인 중복 차단)
    if (processedTx[transactionUuid]) revert AlreadyProcessed();

    // ... 검증 ...

    // 2. 처리 표시 후 이체 실행
    processedTx[transactionUuid] = true;
    if (!ILocalDepositToken(depositToken).forceTransfer(from, to, amount)) {
        revert TransferFailed();
    }
    emit Paid(transactionUuid, from, to, amount);
    return true;
}
```

#### 코드 링크

- `LocalCurrencyPolicy.sol` (`processedTx`, `pay`, `cancelPayment`) :

---

## e. 동시성 제어 (Concurrency)

같은 거래의 동시 진입은 BE의 **Redis 분산 락**으로 먼저 차단하고, 잔액을 실제로 바꾸는 은행 쪽 임계 구간은 **DB 비관적 락**으로 보호한다. 두 락이 계층적으로 역할을 나눈다.

---

### e-1. BE — Redis 분산 락 (SET NX)

#### 기능 설명

여러 애플리케이션 인스턴스가 떠 있어도 같은 결제가 동시에 두 번 실행되지 않도록 Redis 분산 락을 쓴다. `transactionUuid`(취소는 원본 결제 uuid)를 키로 `setIfAbsent`(SET NX) + TTL 30초로 락을 잡고, 성공하면 임계 구간(은행 호출 포함)을 실행한 뒤 `finally`에서 반드시 해제한다.

해제할 때는 단순 `DEL`이 아니라 **Lua compare-and-delete** 스크립트를 쓴다. 락을 걸 때 저장한 자신의 토큰(UUID)과 현재 값이 같을 때만 삭제하므로, TTL이 만료된 뒤 다른 요청이 새로 잡은 락을 실수로 풀어 버리는 문제를 막는다. 락 획득에 실패하면 이미 처리 중이라는 의미이므로 `PAYMENT_ALREADY_PROCESSING`(취소는 `CANCEL_ALREADY_PROCESSING`), HTTP 409를 반환한다. 분산 락(동시 진입 차단)과 멱등 저장소(재요청 식별)가 함께 작동해 중복을 원천 차단한다.

#### 핵심 코드

```java
// RedisPaymentLockManager.withTransactionLock — SET NX로 락 획득, finally에서 안전 해제
public <T> T withTransactionLock(String transactionUuid, Supplier<T> supplier) {
    String key = key(transactionUuid);
    String token = UUID.randomUUID().toString();

    // 1. SET NX로 락 선점. 실패하면 이미 처리 중 → 409
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);
    if (!Boolean.TRUE.equals(acquired)) {
        throw new BusinessException(TransactionErrorCode.PAYMENT_ALREADY_PROCESSING);
    }

    // 2. 임계 구간 실행 후 무조건 해제
    try {
        return supplier.get();
    } finally {
        releaseLock(key, token);   // 아래 Lua 스크립트로 자기 토큰일 때만 DEL
    }
}
```

```lua
-- 락 해제 : 내가 건 락(토큰 일치)일 때만 삭제 (compare-and-delete)
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
end
return 0
```

**호출 지점**: `TransactionCommandService.executePayment`는 `withTransactionLock`으로, `executeCancel`은 `RedisCancelLockManager.withCancelLock`으로 은행 호출을 감싼다.

#### 코드 링크

- `PaymentLockManager` (포트) / `RedisPaymentLockManager.withTransactionLock` :
- `CancelLockManager` (포트) / `RedisCancelLockManager.withCancelLock` :
- 사용처: `TransactionCommandService.executePayment` / `executeCancel` :

---

### e-2. bank — DB 비관적 락 (PESSIMISTIC_WRITE)

#### 기능 설명

은행은 잔액을 실제로 더하고 빼는 구간을 JPA **비관적 락(`@Lock(LockModeType.PESSIMISTIC_WRITE)`)** 으로 보호한다. 지갑·계좌 행을 조회하는 순간 `SELECT ... FOR UPDATE`로 잠가, 동시에 들어온 다른 트랜잭션이 같은 잔액을 함께 읽고 각자 갱신해 버리는 갱신 분실(lost update)을 막는다. 잔액 검증(`ensureSufficientBalance`)과 차감/증가가 하나의 잠금 안에서 직렬화된다.

데드락을 피하려고 **락 획득 순서를 고정**한다.
- 결제: `from` 지갑 → `to` 지갑
- 취소: `to` 지갑 → `from` 지갑 (결제의 역방향)
- 환전: 지갑(`BankWallet`) → 계좌(`BankAccount`)
- 충전: 계좌 → 지갑

추가로 블록체인으로 보내는 거래의 순서를 보장하기 위해, 사용자(지갑 주소)별 시퀀스 번호도 `BlockchainOrderingState` 행을 비관적 락으로 잠그고 원자적으로 발급한다(`BlockchainOrderingSequenceAllocator.allocate`).

정리하면 **BE의 Redis 락은 "같은 거래의 중복 진입"** 을, **bank의 비관적 락은 "서로 다른 거래가 같은 잔액에 동시 접근"** 을 막는다. 두 계층이 합쳐져 멱등성과 잔액 정합성을 함께 보장한다.

#### 핵심 코드

```java
// 비관적 락 리포지토리 — 조회 시점에 행을 잠근다 (SELECT ... FOR UPDATE)
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM BankWallet w WHERE w.walletAddress = :walletAddress")
Optional<BankWallet> findByWalletAddressWithLock(@Param("walletAddress") String walletAddress);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM BankAccount a "
     + "WHERE a.institution.id = :institutionId AND a.accountNumber = :accountNumber")
Optional<BankAccount> findByInstitution_IdAndAccountNumberWithLock(
        @Param("institutionId") Long institutionId, @Param("accountNumber") String accountNumber);
```

```java
// PaymentExecutionService — from → to 순서로 잠그고 잔액 이체 (취소는 to → from 역순)
BankWallet fromWallet = findBankWalletWithLock(request.fromWalletAddress());
BankWallet toWallet   = findBankWalletWithLock(request.toWalletAddress());

ensureSufficientBalance(fromWallet.getBalance(), request.amount());
fromWallet.updateBalance(fromWallet.getBalance().subtract(request.amount()));
toWallet.updateBalance(toWallet.getBalance().add(request.amount()));
```

#### 코드 링크

- 락 리포지토리: `BankWalletRepository.findByWalletAddressWithLock`, `BankAccountRepository.findByInstitution_IdAndAccountNumberWithLock` :
- 사용처: `PaymentExecutionService.payment`, `ExchangeExecutionService.exchange`, `ChargeStateWriter.executeCharge` :
- 시퀀스 발급: `BlockchainOrderingStateJpaRepository.findByOrderingKeyWithLock`, `BlockchainOrderingSequenceAllocator.allocate` :
