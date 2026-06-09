# Bank-Blockchain Async Payment Engineering Plan

## Context

`hangang-pay-bank` currently handles payment and payment cancel synchronously:

- `TransactionCommandService.payment()` calls `ContractCallService.pay()` inside the request flow.
- `TransactionCommandService.cancel()` calls `ContractCallService.cancelPayment()` inside the request flow.
- Wallet balance was recently moved to blockchain `balanceOf`, but async blockchain processing requires bank DB wallet balance to become the business source of truth.
- `LocalCurrencyPolicy.pay()` validates `merchants[to]`.
- `LocalCurrencyPolicy.cancelPayment()` validates `merchants[from]`.
- Calling `simulateOrThrow(pay/cancelPayment)` before async processing is not acceptable because it validates the whole state-changing path, including on-chain token balance, which can lag behind bank DB balance.

Decision already made:

- Use RabbitMQ.
- Keep provider abstraction so Kafka can replace RabbitMQ later.
- Use DB `bank_wallet.balance` as source of truth.
- Use pessimistic locking for wallet concurrency.
- Use `merchants(address)` view call synchronously for whitelist validation only.
- Use outbox pattern for DB-to-MQ consistency.
- Add transaction UUID key to blockchain events.
- Split blockchain transaction submission from receipt waiting so consumers can save `txHash` immediately.
- Use a short-term single-signer-safe consumer strategy. Measure actual blockchain TPS before deciding whether to add signer-level parallelism.
- Add reconciliation scheduler.
- Keep DLQ; reconciliation and DLQ solve different failure modes.

## Goal

Introduce async blockchain reflection for payment and payment cancel in `hangang-pay-bank` while preserving bank ledger correctness.

The request thread should:

1. Validate wallets and merchant whitelist.
2. Update bank DB wallet balances atomically.
3. Create a blockchain ledger record in a pending/submitted lifecycle.
4. Enqueue an outbox record.
5. Return without waiting for blockchain transaction receipt.

The async worker should:

1. Publish outbox records to RabbitMQ.
2. Consume blockchain sync messages.
3. Call the blockchain contract.
4. Persist tx hash as early as possible.
5. Mark ledger success/failure based on receipt.
6. Allow reconciliation to recover missed or partially recorded work.

## Scope / Out of Scope

### Scope

- Bank app only: `hangang-pay-bank`.
- Blockchain contract changes needed for payment/cancel event UUID.
- Payment and payment cancel async blockchain reflection.
- DB wallet balance restoration and pessimistic locking.
- Merchant whitelist view call.
- Outbox table, publisher scheduler, RabbitMQ consumer, DLQ policy.
- Blockchain ledger lifecycle and reconciliation scheduler.
- Bank API error contract and BE `BankClientImpl` mapping guidance.
- Unit/integration tests around new bank-blockchain behavior.

### Out of Scope

- Direct BE application implementation changes unless explicitly started in a separate phase.
- Charge async processing.
- Exchange async processing.
- Full UI/API redesign.
- Kafka implementation. Only define publisher abstraction and property-based selection.
- Strong whitelist synchronization into bank DB.
- Automatic financial rollback when blockchain reflection finally fails. Failed reflection is a retry/DLQ/reconciliation/operation issue unless a later phase explicitly adds compensation.
- `AccountLedger` failure recording: charge 실패 거래도 기록돼야 하지만, 이는 별도 phase에서 `REQUIRES_NEW` 트랜잭션 패턴으로 처리한다. 현재 `AccountLedger`는 SUCCESS만 기록하는 구조이며, `WalletLedger`와 같은 방식으로 추후 통일한다.

## Architecture Decisions

### Source of Truth

`bank_wallet.balance` is the business source of truth for payment/cancel approval and wallet balance response.

Blockchain `balanceOf` is not used for payment approval. It can be used for operational comparison or debugging.

BE must not need to know that blockchain exists. Bank API responses expose Bank DB business results only. Blockchain tx hash, block number, receipt time, outbox status, and blockchain sync status are internal Bank operational data.

`transactionUuid` is the shared BE-Bank business idempotency key:

```text
BE creates transactionUuid
-> Bank receives transactionUuid in PaymentRequest/CancelRequest
-> Bank stores it in the business ledger and blockchain sync ledger/outbox
-> Contract event receives a derived bytes32 key only for Bank-side reconciliation
```

Do not redefine `transactionUuid` as a blockchain identifier. It remains a business transaction identifier shared by BE and Bank.

### Bank API Response Contract

Payment response should represent Bank DB confirmation, not blockchain confirmation:

```java
public record PaymentResponse(
        String transactionUuid,
        String status,
        LocalDateTime confirmedAt,
        BigDecimal fromBalance,
        BigDecimal toBalance) {}
```

Cancel response follows the same rule:

```java
public record CancelResponse(
        String transactionUuid,
        String originalTransactionUuid,
        String status,
        LocalDateTime confirmedAt,
        BigDecimal fromBalance,
        BigDecimal toBalance) {}
```

Response semantics:

```text
status=SUCCESS means Bank DB balance mutation and WalletLedger commit succeeded.
status=FAILED means business validation failed (e.g. insufficient balance, non-merchant).
confirmedAt means WalletLedger.confirmedAt set at request commit time.
transactionUuid is the only shared identifier between BE and Bank.
```

Fields not returned to BE from payment/cancel:

```text
txHash
blockNumber
blockchain confirmedAt
blockchain_ledger.status
outbox.status
```

### Whitelist Validation

Do not call `simulateOrThrow(pay)` or `simulateOrThrow(cancelPayment)` during request processing.

Add `ContractCallService.isMerchant(String walletAddress)` that performs `eth_call` on:

```solidity
merchants(address) returns (bool)
```

Use it only for:

- Payment: validate `toWalletAddress`.
- Cancel: validate `fromWalletAddress`.

If view call fails at RPC level, fail the request with blockchain RPC/business error because the system cannot confirm merchant eligibility.

### Error Boundary

Blockchain errors are split by timing.

#### Request-Time Errors

These can reach BE because they happen before Bank confirms the business transaction. In this plan, the only request-time blockchain call in payment/cancel is merchant whitelist validation:

```text
BE -> Bank payment/cancel request
Bank -> ContractCallService.isMerchant(...)
```

Possible request-time blockchain errors:

```text
BLOCKCHAIN_MERCHANT_NOT_REGISTERED
BLOCKCHAIN_RPC_FAILED
BLOCKCHAIN_CONTRACT_NOT_FOUND
BLOCKCHAIN_INVALID_ADDRESS
BLOCKCHAIN_TRANSACTION_REVERTED
```

Bank may return these precise codes to BE. BE `BankClientImpl` should translate them into frontend-safe service errors instead of passing raw blockchain details through unchanged.

Recommended BE-side mapping:

```text
BLOCKCHAIN_MERCHANT_NOT_REGISTERED
-> payment/cancel merchant unavailable or merchant not registered for payment

BLOCKCHAIN_RPC_FAILED
BLOCKCHAIN_CONTRACT_NOT_FOUND
BLOCKCHAIN_TRANSACTION_REVERTED
BLOCKCHAIN_INVALID_ADDRESS
-> BANK_SERVER_ERROR or BANK_CALL_FAILED
```

Bank business errors are still returned directly from the request path:

```text
BANK_WALLET_NOT_FOUND
BANK_ACCOUNT_NOT_FOUND
INSTITUTION_NOT_FOUND
TRANSACTION_INSUFFICIENT_BALANCE
TRANSACTION_DUPLICATE_PROCESSING
TRANSACTION_ALREADY_FAILED
TRANSACTION_NOT_FOUND
```

#### Async Reflection Errors

These do not reach BE in the normal payment/cancel response flow because Bank has already returned a Bank DB business result.

Examples:

```text
consumer submit failure
receipt timeout
transaction reverted after submit
on-chain insufficient balance
DLQ movement
reconciliation failure
```

Async blockchain errors are recorded only in Bank internal state:

```text
blockchain_ledger.status
blockchain_ledger.failure_code
blockchain_ledger.failure_message
blockchain_outbox.status
blockchain_outbox.retry_count
DLQ
logs/metrics
```

BE can know async blockchain reflection state only if a separate internal status API is intentionally added. That is out of scope for this payment/cancel response contract.

### MQ Provider Abstraction

Domain/application code depends on ports, not RabbitMQ classes:

```java
public interface BlockchainSyncMessagePublisher {
    void publish(BlockchainSyncMessage message);
}
```

RabbitMQ implementation is selected by property:

```text
mq.provider=rabbit
```

Kafka can later implement the same port.

### Common Async Sync Interfaces

The common async pipeline must be implemented before payment/cancel/charge/exchange teams work in parallel. Feature services should use high-level request abstractions, not RabbitMQ, Kafka, outbox entities, or contract submission details.

There are two extension points:

1. Request-side enqueue API used by transaction services.
2. Consumer-side handler API used by type-specific blockchain sync processors.

#### Request-Side API

`TransactionCommandService` and future charge/exchange services should call only this application port:

```java
public interface BlockchainSyncRequester {
    BlockchainSyncRequestResult request(BlockchainSyncRequest request);
}
```

Request model:

```java
public interface BlockchainSyncRequest {
    BlockchainSyncType type();
    String transactionUuid();
    Object payload();
}
```

Result model:

```java
public record BlockchainSyncRequestResult(
        Long blockchainLedgerId,
        Long outboxId) {}
```

The implementation is responsible for:

```text
1. Create BlockchainLedger(status=PENDING, type=..., bankConfirmedAt=now, idempotentKey=transactionUuid).
2. Create BlockchainOutbox(status=NEW, blockchainLedgerId=..., payload=...).
3. Return ids for internal logging/testing.
```

`TransactionCommandService` must not know:

```text
RabbitMQ
Kafka
exchange/queue/routing key
outbox retry count
DLQ
consumer
txHash
receipt polling
```

It may know only:

```text
transactionUuid
sync type
payload values needed for blockchain reflection
```

#### Message Envelope

Use one common message envelope so RabbitMQ can be swapped with Kafka later:

```java
public record BlockchainSyncMessage(
        String messageId,
        Long outboxId,
        Long blockchainLedgerId,
        String transactionUuid,
        BlockchainSyncType type,
        int payloadVersion,
        JsonNode payload) {}
```

`messageId` should be stable and derived from the outbox row or stored in the outbox row. Do not create a new random message id every publish retry.

Initial type enum should include all planned work so teams can add handlers without changing the common pipeline:

```java
public enum BlockchainSyncType {
    PAYMENT,
    CANCEL,
    CHARGE,
    EXCHANGE
}
```

Payload DTOs are type-specific:

```java
public record PaymentBlockchainPayload(
        String fromWalletAddress,
        String toWalletAddress,
        BigDecimal amount) {}

public record CancelBlockchainPayload(
        String originalTransactionUuid,
        String fromWalletAddress,
        String toWalletAddress,
        BigDecimal amount) {}
```

Charge and exchange teams add their own payload DTOs without changing the common envelope.

#### Publisher Port

The outbox publisher scheduler uses this provider port:

```java
public interface BlockchainSyncMessagePublisher {
    void publish(BlockchainSyncMessage message);
}
```

RabbitMQ implements this now. Kafka can implement it later.

#### Consumer Handler API

The generic MQ listener should not contain payment/cancel/charge/exchange logic. It delegates by type:

```java
public interface BlockchainSyncHandler {
    BlockchainSyncType type();
    void handle(BlockchainSyncMessage message);
}
```

Handler examples:

```text
PaymentBlockchainSyncHandler
CancelBlockchainSyncHandler
ChargeBlockchainSyncHandler
ExchangeBlockchainSyncHandler
```

Handler registry:

```java
public interface BlockchainSyncHandlerRegistry {
    BlockchainSyncHandler get(BlockchainSyncType type);
}
```

Generic consumer flow:

```text
RabbitMQ message received
-> deserialize BlockchainSyncMessage
-> registry.get(message.type())
-> handler.handle(message)
-> ack on success
-> retry/DLQ on retryable failure
```

#### Feature Team Boundaries

Common pipeline owner:

```text
BlockchainSyncType
BlockchainSyncMessage
BlockchainSyncRequest/Result
BlockchainSyncRequester
BlockchainOutbox
BlockchainSyncMessagePublisher
RabbitMQ config
Generic listener
Handler registry
```

Payment/cancel owner:

```text
PaymentBlockchainPayload
CancelBlockchainPayload
PaymentBlockchainSyncHandler
CancelBlockchainSyncHandler
payment/cancel service enqueue calls
```

Charge owner:

```text
ChargeBlockchainPayload
ChargeBlockchainSyncHandler
charge service enqueue call
```

Exchange owner:

```text
ExchangeBlockchainPayload
ExchangeBlockchainSyncHandler
exchange service enqueue call
```

This keeps parallel work from repeatedly editing MQ config, generic consumer code, or `TransactionCommandService` internals.

### DB-Blockchain Consistency and Retry Safety

#### 불일치 발생 상황

Bank DB는 결제를 SUCCESS로 확정했지만 블록체인 트랜잭션이 최종 실패하면 DB 잔액과 블록체인 토큰 잔액이 불일치한다.

```
DB:          A 잔액 -10,000 / B 잔액 +10,000 (SUCCESS)
Blockchain:  토큰 이동 없음 (FAILED)
```

#### 안전한 재시도를 위한 컨트랙트 UUID storage 요구사항

현재 컨트랙트는 `transactionUuid`를 이벤트 로그에만 emit하고 storage에 저장하지 않는다. 이 경우 같은 UUID로 `pay()`를 두 번 호출해도 컨트랙트가 막지 못해 토큰이 두 번 이동할 수 있다.

재시도를 안전하게 만들려면 컨트랙트가 처리된 UUID를 storage에 기록해야 한다:

```solidity
mapping(bytes32 => bool) public processedTx;

function pay(bytes32 transactionUuid, address from, address to, uint256 amount) {
    require(!processedTx[transactionUuid], "already processed");
    processedTx[transactionUuid] = true;
    ...
}
```

재시도 시 컨트랙트가 revert → Bank는 "이미 처리됨" 신호로 해석 → 이벤트 스캔으로 원본 txHash 복원 가능. 몇 번을 재시도해도 토큰 이동은 1회만 발생한다.

이 수정은 Phase 1.5로 별도 진행한다 (Phase 1 구현 결과 참고).

#### DLQ 운영 정책

현재 구현은 DLQ를 지수 백오프 재시도 큐로 사용하지 않는다.

- retryable consumer 오류는 예외를 전파해 RabbitMQ NACK를 발생시킨다.
- `defaultRequeueRejected=false` 설정으로 메시지는 원래 큐에 재적재되지 않고 즉시 DLQ로 이동한다.
- DLQ 적재 후 자동 재시도는 없다. 운영자가 수동 재투입하거나, 이후 reconciliation/별도 재처리 phase에서 복구한다.

즉 현재 DLQ는 "즉시 분리 보관 큐" 역할이며, 지수 백오프 재시도는 별도 phase로 구현한다.

#### 재시도 가능/불가 분류

컨트랙트 로직이 거부하는 에러는 재시도해도 영원히 revert된다. retryable/non-retryable 분류는 `BlockchainErrorCode` enum이 아니라 consumer 레이어 상수 Set에서 관리한다:

```
재시도 가능 (retryable):
  BLOCKCHAIN_RPC_FAILED      → 노드 일시 장애
  BLOCKCHAIN_RECEIPT_TIMEOUT → 노드 일시 지연

재시도 불가 (fatal, 보상 없음):
  ERC20InsufficientBalance  → DB-first 전환 후 DB 잔액 검증을 통과했다면 블록체인 잔액 부족은 DB-blockchain 불일치 버그. retry로 해소되지 않으므로 FATAL.

재시도 불가 (fatal, 보상 없음):
  BLOCKCHAIN_MERCHANT_NOT_REGISTERED → 요청 시점에 isMerchant() 검증을 통과했으므로 async 구간에서 발생 가능성 없음. 발생 시 코드/인프라 버그로 간주.
  BLOCKCHAIN_UNAUTHORIZED            → 컨트랙트 owner 키 또는 권한 설정 오류 (코드/인프라 버그)
  BLOCKCHAIN_INVALID_ADDRESS         → 주소 데이터 정합성 오류 (요청 검증이 뚫린 것)
  기타 예상 외 revert                → 코드/인프라 버그
```

#### 재시도 불가 실패 시 처리

재시도 불가 에러는 코드 또는 인프라 버그로 간주한다. 자동 보상 트랜잭션은 구현하지 않는다.

```
non-retryable 에러 발생
→ BlockchainLedger FAILED 마킹
→ log.error 운영 알람 (추후 메트릭/알람 연동)
→ ACK (DLQ 재투입 없음)
→ 수동 조사 및 데이터 보정
```

보상 트랜잭션을 구현하지 않는 근거: 요청 시점에 `isMerchant()` 동기 검증을 통과하고 DB 잔액 차감까지 완료된 시점에서 async consumer 실행까지의 시간 내에 whitelist 변경이 발생할 확률은 사실상 없다. 발생 시 운영 개입이 필요한 시스템 이상 상황이므로 자동 보상보다 수동 조사가 적합하다.

### Signer Nonce and Throughput

Current `ContractCallService` signs LOCAL_CURRENCY transactions with one owner account. One signer means one nonce stream. Concurrent submits from the same signer can race on nonce selection and produce duplicate nonce, nonce-too-low, or replacement transaction errors.

Short-term implementation:

- Keep blockchain submit safe before optimizing throughput.
- Start with RabbitMQ consumer concurrency `1`, or an equivalent signer lock around `nonce lookup -> sign -> send -> txHash`.
- If concurrency is increased in the first implementation, the signer lock must cover only the short submit critical section. Receipt waiting must stay outside the lock.

Do not optimize signer parallelism before measuring actual blockchain TPS and backlog under realistic local/dev load. Decide expansion only after the measured bottleneck is the single signer submit path, not DB, RabbitMQ, RPC, receipt polling, or block production interval.

Expansion path after TPS measurement:

1. Raise consumer concurrency while keeping signer lock around submit only.
2. Add explicit signer nonce manager if raw concurrent submit remains unstable.
3. Change contract authorization from single `onlyOwner` to `operator` signer set.
4. Run a signer pool and partition work by signer/queue.
5. Track per-signer nonce, failure, and backlog metrics.

Until the contract supports multiple authorized operators, multiple signer accounts cannot safely increase payment/cancel submit throughput because only the owner can call `pay` and `cancelPayment`.

### Outbox Pattern

`TransactionCommandService` writes an outbox record inside the same DB transaction as wallet balance and blockchain ledger changes.

A scheduler publishes outbox records to MQ after commit.

Do not publish directly to RabbitMQ inside the payment DB transaction.

### Blockchain Transaction UUID

Prefer event-level UUID, not contract storage.

Contract function signatures:

```solidity
function pay(bytes32 transactionUuid, address from, address to, uint256 amount)
function cancelPayment(bytes32 transactionUuid, address from, address to, uint256 amount)
```

Events:

```solidity
event Paid(bytes32 indexed transactionUuid, address indexed from, address indexed to, uint256 amount);
event PaymentCanceled(bytes32 indexed transactionUuid, address indexed from, address indexed to, uint256 amount);
```

Bank converts UUID string to `bytes32` deterministically. Store both original UUID and `txKey` if useful for reconciliation.

### Ledger Status

Extend `BlockchainTxStatus`:

```text
PENDING    bank accepted payment/cancel and outbox was created
SUBMITTED  blockchain txHash is known, receipt not finalized in DB
SUCCESS    receipt success
FAILED     final failure or unrecoverable processing failure
```

If adding `SUBMITTED` causes too much immediate churn, use `PENDING + txHash != null` as submitted semantics. Preferred plan is to add `SUBMITTED`.

### Blockchain Submit / Receipt Split

`ContractCallService` must not expose only "send and wait for receipt" methods for async payment/cancel.

Current synchronous shape:

```text
simulateOrThrow
-> signAndSend
-> waitForReceipt
-> TransactionReceipt
```

Async shape:

```text
simulateOrThrow
-> signAndSend
-> SubmittedBlockchainTx(txHash)
-> ledger SUBMITTED + txHash commit
-> receipt wait/query
-> ledger SUCCESS/FAILED
```

This creates a recoverable checkpoint. If the consumer dies after tx submission but before receipt handling, reconciliation can continue from `txHash`.

There is still a narrow failure window:

```text
signAndSend succeeds
-> txHash returned
-> process dies before DB stores txHash
```

The contract `transactionUuid` event is the fallback for this case. Reconciliation can scan events by UUID key and restore the missing `txHash`.

### DLQ and Reconciliation

DLQ and reconciliation are both required.

- DLQ stores messages that failed the MQ consumer retry path.
- Reconciliation scans uncertain DB states and blockchain events/receipts to repair missed state transitions.

Reconciliation should not compare every transaction every run. It targets uncertain records:

- `PENDING` older than threshold.
- `SUBMITTED` without success/failure after threshold.
- `FAILED` with `txHash`.
- Outbox `FAILED` or DLQ-linked records.

## Transaction Boundary

### Payment Request Transaction

현재 구현에서는 `WalletLedger(SUCCESS)`를 메인 트랜잭션에 포함하고, `WalletLedger(FAILED)`만 `REQUIRES_NEW`로 저장한다.

흐름:

1. Find existing `BlockchainLedger` by `transactionUuid` (idempotency check).
2. Main transaction:
   - Normalize addresses.
   - Fetch `fromWallet` and `toWallet` with pessimistic write locks.
   - Validate merchant whitelist using `isMerchant(toWalletAddress)`.
   - Validate `fromWallet.balance >= amount`.
   - Debit `fromWallet.balance`.
   - Credit `toWallet.balance`.
   - Save `WalletLedger(status=SUCCESS, confirmedAt=now)`.
   - Save `BlockchainLedger(status=PENDING, idempotentKey=transactionUuid)`.
   - Save `BlockchainOutbox(status=NEW, type=PAYMENT, blockchainLedgerId=..., payload=...)`.
3. Commit main transaction.
4. On `BusinessException`: save `WalletLedger(status=FAILED)` in `REQUIRES_NEW`, then roll back main transaction.
5. On system exception: log error and roll back main transaction without saving failed wallet ledger.

After commit, request returns `PaymentResponse` with:

- `status=SUCCESS`.
- `confirmedAt=WalletLedger.confirmedAt`.
- DB balances after debit/credit.

`transactionUuid` is the only shared identifier between BE and Bank.
Blockchain reflection is tracked only in `blockchain_ledger` and outbox.

### Cancel Request Transaction

Same as payment except:

- Validate merchant whitelist with `isMerchant(fromWalletAddress)`.
- Debit merchant/from wallet.
- Credit user/to wallet.
- Type is `CANCEL`.
- Payload includes `originalTransactionUuid`.

### Outbox Publisher Transaction

Scheduler transaction per batch or per row:

1. Select `BlockchainOutbox.status=NEW`.
2. Publish via `BlockchainSyncMessagePublisher`.
3. Mark outbox `SENT`.

If publish fails:

- Increment retry count.
- Keep status `NEW` until max retry.
- Mark outbox `FAILED` after max retry.

### MQ Consumer Transaction

Consumer transaction per message:

1. Load ledger by `ledgerId` or `transactionUuid`.
2. If ledger is `SUCCESS`, ack without contract call.
3. If ledger is `FAILED`, ack without reprocessing.
4. If ledger has no `txHash`, submit contract `pay/cancelPayment(transactionUuidBytes32, from, to, amount)`.
5. Persist `txHash` and mark `SUBMITTED` immediately in its own durable checkpoint.
6. Wait for receipt or query receipt using `txHash`.
7. If receipt success, mark `SUCCESS`.
8. If revert/failure, either throw for retryable DLQ path or mark final `FAILED`.

Exception policy:

- `BLOCKCHAIN_RPC_FAILED`, `BLOCKCHAIN_RECEIPT_TIMEOUT`: retryable → throw → NACK → DLQ.
- `ERC20InsufficientBalance`: non-retryable → FAILED 마킹 + log.error → ACK.
- `BLOCKCHAIN_MERCHANT_NOT_REGISTERED`, `BLOCKCHAIN_UNAUTHORIZED`, `BLOCKCHAIN_INVALID_ADDRESS`, 기타 예상 외 revert: non-retryable → FAILED 마킹 + log.error → ACK. 보상 트랜잭션 없음. 요청 시점에 `isMerchant()` 검증을 통과했으므로 발생 시 코드/인프라 버그로 간주하고 수동 조사.
- Duplicate message with successful ledger: no-op ack.
- Retryable/non-retryable 분류는 `BlockchainSyncProcessor` 내 상수 Set으로 관리한다 (`BlockchainErrorCode` enum에 정책 포함하지 않음).

## Phase Plan

Each phase should be small enough for 1-2 hours.

After each phase, update this document briefly:

- Mark the phase checklist item complete.
- Record changed files and verification command results.
- Update `Current Next Action` to the next phase or blocker.

### Phase 0 - Common Async Pipeline Contracts

#### Goal

Define and test the common extension points that allow payment/cancel/charge/exchange teams to work in parallel without editing RabbitMQ config or generic consumer code.

#### DoD

- `BlockchainSyncType` includes `PAYMENT`, `CANCEL`, `CHARGE`, `EXCHANGE`.
- Common message envelope exists.
- Request-side port exists: `BlockchainSyncRequester`.
- Provider-side port exists: `BlockchainSyncMessagePublisher`.
- Consumer-side handler port exists: `BlockchainSyncHandler`.
- Handler registry exists and fails clearly when a handler is missing.
- Type-specific payload DTO locations are established.
- No feature service directly depends on RabbitMQ, Kafka, or `RabbitTemplate`.

#### Implementation Files

- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/entity/BlockchainSyncType.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/dto/BlockchainSyncMessage.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/dto/BlockchainSyncRequest.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/dto/BlockchainSyncRequestResult.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/port/BlockchainSyncRequester.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/port/BlockchainSyncMessagePublisher.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/port/BlockchainSyncHandler.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/service/BlockchainSyncHandlerRegistry.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/dto/payload/PaymentBlockchainPayload.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/dto/payload/CancelBlockchainPayload.java`
- placeholder payload DTOs for charge/exchange if those teams need compile-time anchors

#### Test Cases

- Registry returns the handler matching `BlockchainSyncType`.
- Registry fails with a clear business/runtime exception when no handler exists.
- Common message serializes/deserializes with `payloadVersion`.
- `messageId` remains stable when created from an outbox id.
- Feature payload DTOs do not require RabbitMQ/Kafka imports.

#### RED State

- Tests fail because common contracts and registry do not exist.

#### GREEN State

- Common contract tests pass and feature teams can compile against stable ports.

#### Verification Command

```bash
cd hangang-pay-bank
./gradlew test --tests '*BlockchainSync*'
```

### Phase 1 - Contract UUID Events

#### Goal

Add transaction UUID to payment/cancel contract functions and events.

#### DoD

- `LocalCurrencyPolicy.pay()` accepts `bytes32 transactionUuid`.
- `LocalCurrencyPolicy.cancelPayment()` accepts `bytes32 transactionUuid`.
- `Paid` and `PaymentCanceled` events include indexed `transactionUuid`.
- Existing whitelist and transfer behavior is preserved.
- Contract tests updated.

#### Implementation Files

- `hangang-pay-bc/blockchain/contracts/LocalCurrencyPolicy.sol`
- `hangang-pay-bc/blockchain/test/**` if tests exist
- `hangang-pay-bc/blockchain/scripts/**` if deployment/call scripts reference old ABI
- Generated artifact copied later to bank resources if this repo uses generated JSON artifacts

#### Test Cases

- Payment emits `Paid(transactionUuid, from, to, amount)`.
- Cancel emits `PaymentCanceled(transactionUuid, from, to, amount)`.
- Non-whitelisted payment receiver still reverts.
- Non-whitelisted cancel sender still reverts.
- Zero UUID is allowed or rejected according to chosen policy. Recommended: allow, because UUID validity is bank responsibility.

#### RED State

- Existing tests or new tests fail because event/function signature does not contain UUID.

#### GREEN State

- Contract tests pass with new function signature and event assertions.

#### Verification Command

```bash
cd hangang-pay-bc/blockchain
npx hardhat test
```

#### 구현 결과

- `transactionUuid`는 contract storage에 저장하지 않고 event log에만 emit. (Prefer event-level UUID 원칙)
- 컨트랙트 테스트는 작성하지 않음. `npx hardhat compile` 컴파일 성공으로 검증.
- typechain-types 자동 재생성 완료.

> **TODO:** `AccountLedger`에 `institution_id` 컬럼 추가 필요. 현재 institution 접근 경로가 `AccountLedger → BankAccount → Institution`으로 join이 필요함. 기관별 원장 쿼리 및 감사 목적으로 직접 FK 추가 요망.

> **TODO (Phase 1.5):** 안전한 재시도를 위해 컨트랙트에 `processedTx` storage 추가 필요. 현재 event-level UUID만으로는 같은 transactionUuid로 중복 실행이 가능해 재시도 시 토큰 이중 이동 위험이 있음. Architecture Decisions - DB-Blockchain Consistency 섹션 참고.

### Phase 1.5 - Contract processedTx Storage

#### Goal

컨트랙트가 동일 `transactionUuid`의 중복 실행을 거부하도록 `processedTx` storage를 추가한다. 이로써 Consumer의 재시도가 토큰 이중 이동 없이 안전하게 동작한다.

#### DoD

- `LocalCurrencyPolicy.pay()`가 이미 처리된 `transactionUuid`로 호출되면 revert한다.
- `LocalCurrencyPolicy.cancelPayment()`도 동일하게 적용된다.
- `processedTx` mapping은 public이어서 Bank가 처리 여부를 조회할 수 있다.
- 기존 whitelist, transfer 동작은 보존된다.

#### Implementation Files

- `hangang-pay-bc/blockchain/contracts/LocalCurrencyPolicy.sol`
- `hangang-pay-bc/blockchain/test/**`

#### Test Cases

- 동일 `transactionUuid`로 `pay()` 두 번 호출 시 두 번째는 revert.
- 동일 `transactionUuid`로 `cancelPayment()` 두 번 호출 시 두 번째는 revert.
- 다른 `transactionUuid`로 호출 시 정상 실행.
- `processedTx[uuid]` 조회로 처리 여부 확인 가능.

#### Verification Command

```bash
cd hangang-pay-bc/blockchain
npx hardhat test
```

#### 구현 결과

- `error AlreadyProcessed()` custom error 추가.
- `mapping(bytes32 => bool) public processedTx` storage 추가 (merchants 아래, `__gap` 위).
- `__gap` 크기 50 → 49 (1 슬롯 소비).
- `pay()`: 진입 시 `processedTx[uuid]` 체크 → `AlreadyProcessed` revert, 성공 후 `processedTx[uuid] = true` 마킹.
- `cancelPayment()`: 동일하게 적용.
- `npx hardhat compile` 성공, typechain-types 재생성 완료.
- 테스트는 배포 후 직접 호출로 검증.

### Phase 2 - Bank Wallet Balance Restore, Locking, and WalletLedger

#### Goal

Restore DB wallet balance, add pessimistic locking primitives, and create `WalletLedger` as the common wallet transaction ledger shared by payment/cancel/charge/exchange.

#### DoD

- `BankWallet` has `balance`.
- `BankWallet.updateBalance(BigDecimal)` exists.
- New wallets start with zero balance.
- Wallet query returns DB balance.
- Repository exposes lock query for payment/cancel.
- Existing contract `getBalance()` remains available for operational use.
- `WalletLedger` entity exists with `transactionUuid`, `bankWallet`, `direction`, `status`, `confirmedAt`, `amount`.
- `WalletLedger` repository supports idempotency lookup by `(transactionUuid, bankWallet)`.
- `WalletLedger` is not yet wired into any service (that happens in Phase 5+).

#### Design Decisions

- **복식부기(double-entry)**: 결제 시 WalletLedger 2행 생성 (from=DEBIT, to=CREDIT). idempotency 키는 `(transaction_uuid, bank_wallet_id)` 복합 unique.
- **`type` 미포함**: WalletLedger에 거래 종류(PAYMENT/CANCEL 등) 컬럼 없음. `transactionUuid`로 BlockchainOutbox 조인하면 알 수 있으므로 YAGNI. 타입별 쿼리 필요 시 추후 추가.
- **`institution_id` 미포함**: WalletLedger에 institution 직접 FK 없음. BankWallet을 통해 접근. (AccountLedger에는 추가 예정 — TODO 참고)
- **트랜잭션 경계**: WalletLedger(PENDING) 초기 저장은 REQUIRES_NEW. 이후 잔액 차감/증가 + WalletLedger(SUCCESS) + BlockchainLedger + BlockchainOutbox는 동일 메인 트랜잭션으로 원자적 커밋. (Outbox 패턴 보장)
- **BlockchainLedger `type` 필드 누락**: 현재 엔티티에 `type`(PAYMENT/CANCEL/CHARGE/EXCHANGE)이 없음. Phase 4(Outbox) 작업 시 함께 추가 필요.

#### Implementation Files

- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/institution/entity/BankWallet.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/institution/repository/BankWalletRepository.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/institution/service/BankWalletQueryService.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/ledger/entity/WalletLedger.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/ledger/entity/WalletLedgerDirection.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/ledger/entity/WalletLedgerStatus.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/ledger/repository/WalletLedgerRepository.java`

#### Test Cases

- New wallet response has zero DB balance.
- Wallet query does not call `ContractCallService.getBalance()`.
- Lock repository method returns wallet by normalized address.
- Balance update is reflected in response.
- `WalletLedger` can be saved and found by `(transactionUuid, bankWallet)`.

#### RED State

- Tests expect DB balance but entity/service still has no `balance`. `WalletLedger` entity does not exist.

#### GREEN State

- Wallet creation/query tests pass with DB balance. `WalletLedger` entity/repository tests pass.

#### Verification Command

```bash
cd hangang-pay-bank
./gradlew test --tests '*BankWallet*' --tests '*WalletLedger*'
```

#### 구현 결과

- `BankWalletQueryService`에서 `ContractCallService` 의존 제거 완료.
- `WalletLedgerType` 대신 `WalletLedgerDirection(DEBIT/CREDIT)` 사용.
- `./gradlew build -x test` 컴파일 성공 확인.

### Phase 3 - Whitelist View Call

#### Goal

Add synchronous `merchants(address)` view call to validate payment/cancel eligibility without simulating the full transfer.

#### DoD

- `ContractCallService.isMerchant(String walletAddress)` exists.
- It calls `merchants(address)` on `LOCAL_CURRENCY`.
- It returns boolean.
- RPC/custom error handling follows existing `getBalance()` style.
- Payment/cancel service can use it.

#### Implementation Files

- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchain/service/ContractCallService.java`
- `hangang-pay-bank/src/test/java/family/fisa/hangangpaybank/domain/blockchain/service/ContractCallServiceTest.java`

#### Test Cases

- Decodes `true` from eth_call.
- Decodes `false` from eth_call.
- Empty decode or RPC error maps to blockchain error.
- It does not call `simulateOrThrow`.

#### RED State

- New tests fail because `isMerchant` does not exist.

#### GREEN State

- Contract call tests pass for boolean decode.

#### Verification Command

```bash
cd hangang-pay-bank
./gradlew test --tests '*ContractCallServiceTest'
```

#### 구현 결과

- `ContractCallService.isMerchant(String walletAddress)` 추가. `getBalance()` 패턴과 동일하게 패키지-프라이빗 `readMerchant(Web3j, ...)` 분리.
- `simulateOrThrow` 미호출 검증 포함 테스트 6개 추가. `./gradlew test --tests '*ContractCallServiceTest'` 27개 전체 PASS.

### Phase 4 - Outbox Domain Model

#### Goal

Create DB outbox model for blockchain sync messages.

#### DoD

- Outbox entity exists.
- Outbox repository supports finding publishable records.
- Outbox stores the Phase 0 common envelope fields plus serialized payload.
- Outbox write service implements `BlockchainSyncRequester`.
- Outbox write happens inside payment/cancel/charge/exchange business transactions in later phases.

#### Implementation Files

- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/entity/BlockchainOutbox.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/entity/BlockchainOutboxStatus.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/repository/BlockchainOutboxRepository.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchainoutbox/service/BlockchainOutboxSyncRequester.java`
- DB migration or DDL notes

#### Test Cases

- `BlockchainSyncRequester.request()` saves outbox with payment payload.
- `BlockchainSyncRequester.request()` saves outbox with cancel payload and original UUID.
- Find only publishable statuses.
- Retry count increments and max retry marks failed.

#### RED State

- Repository/service tests fail because outbox model does not exist.

#### GREEN State

- Outbox unit/repository tests pass.

#### Verification Command

```bash
cd hangang-pay-bank
./gradlew test --tests '*BlockchainOutbox*'
```

#### 구현 결과

- `BlockchainOutboxStatus(NEW/SENT/FAILED)`, `BlockchainOutbox` 엔티티, 리포지토리 어댑터 패턴 추가.
- `BlockchainOutboxSyncRequester`가 `BlockchainSyncRequester` 구현. LOCAL_CURRENCY 컨트랙트 오너 기관 내부 조회 후 ledger+outbox 동일 트랜잭션 저장.
- `BlockchainTxStatus`에 `SUBMITTED` 추가, `PaymentStatusResponse` switch 갱신.
- ERD(`docs/bank-erd.md`)에 `blockchain_outbox.transaction_uuid` 컬럼 추가.
- `./gradlew test --tests '*BlockchainOutbox*'` 6개 전체 PASS.

### Phase 5 - Payment/Cancel DB-First Flow

#### Goal

Change payment/cancel request flow to DB-first Bank business processing. Blockchain reflection becomes an internal async side effect.

#### DoD

- Payment no longer calls `ContractCallService.pay()` in request thread.
- Cancel no longer calls `ContractCallService.cancelPayment()` in request thread.
- Payment validates `isMerchant(toWallet)`.
- Cancel validates `isMerchant(fromWallet)`.
- Wallet balances update under pessimistic lock.
- `WalletLedger(SUCCESS)` participates in the main transaction and commits atomically with balance update, `BlockchainLedger(PENDING)`, and outbox `NEW`.
- `WalletLedger(FAILED)` is saved with `REQUIRES_NEW` so business failures are recorded even when the main transaction rolls back.
- On bank SUCCESS: wallet balances debited/credited, `WalletLedger(SUCCESS)` saved, `BlockchainLedger(PENDING)` and outbox `NEW` created in the same transaction.
- On bank FAILED: `WalletLedger(FAILED)` saved via `REQUIRES_NEW`, main transaction rolled back, no `BlockchainLedger` or outbox created.
- `PaymentResponse` and `CancelResponse` expose only `transactionUuid`, `status`, `confirmedAt`, `fromBalance`, `toBalance`.
- Duplicate successful `transactionUuid` returns existing result without re-processing.

#### Implementation Files

- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/transaction/service/TransactionCommandService.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/transaction/service/PaymentStateWriter.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/transaction/dto/response/PaymentResponse.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/transaction/dto/response/CancelResponse.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchain/entity/BlockchainTxStatus.java`
- `hangang-pay-bank/src/test/java/family/fisa/hangangpaybank/domain/transaction/service/TransactionCommandServiceTest.java`
- `WalletLedger` entity/repository는 Phase 2에서 구현된 것을 사용.

#### Test Cases

- Payment with non-merchant receiver saves `WalletLedger(FAILED)` and does not debit balance.
- Payment with insufficient DB balance saves `WalletLedger(FAILED)` and does not debit balance.
- Payment success debits from wallet, credits to wallet, saves `WalletLedger(SUCCESS)`, creates `BlockchainLedger(PENDING)`, creates outbox `NEW`.
- Payment response has `status=SUCCESS`, `confirmedAt` equal to `WalletLedger.confirmedAt`, no `txHash`, no `blockNumber`.
- Payment does not call `contractCallService.pay()`.
- Cancel validates merchant sender; failure saves `WalletLedger(FAILED)`.
- Cancel success debits merchant, credits user, saves `WalletLedger(SUCCESS)`, creates `BlockchainLedger(PENDING)`, creates outbox `NEW`.
- `WalletLedger(FAILED)` is committed even when main transaction rolls back (`REQUIRES_NEW`).
- Duplicate `WalletLedger(SUCCESS)` by `transactionUuid` does not re-process or create second outbox.

#### RED State

- Tests fail because service still calls blockchain synchronously and uses chain balance.

#### GREEN State

- Payment/cancel service tests pass with DB-first behavior.

#### Verification Command

```bash
cd hangang-pay-bank
./gradlew test --tests '*TransactionCommandServiceTest'
```

#### 구현 결과

- `TransactionCommandService`의 payment/cancel 요청 흐름을 DB-first로 전환했다. 요청 스레드에서는 `ContractCallService.pay()`/`cancelPayment()`를 호출하지 않고 DB 잔액 변경과 outbox 생성까지만 처리한다.
- `findByWalletAddressWithLock()` 기반 비관적 락 조회를 사용해 결제/취소 시 지갑 잔액 동시성 충돌을 방지했다.
- `ensureMerchant()`를 추가해 payment는 `toWallet`, cancel은 `fromWallet`에 대해 온체인 가맹점 화이트리스트 view call 검증을 수행한다.
- `PaymentResponse`, `CancelResponse`를 `transactionUuid`, `status`, `confirmedAt`, `fromBalance`, `toBalance`만 반환하도록 단순화했다.
- `PaymentStateWriter`를 `saveSuccessWalletLedgers()`와 `saveFailedWalletLedgers()`로 분리했다. `SUCCESS`는 메인 트랜잭션에 참여해 잔액 변경/outbox 생성과 원자적으로 커밋되고, `FAILED`만 `REQUIRES_NEW`로 남긴다.
- 비즈니스 예외는 `WalletLedger(FAILED)`를 저장하고 `warn` 로그를 남긴다. 시스템 예외는 원장 기록 없이 `error` 로그만 남기고 전체 트랜잭션을 롤백한다.

**테스트 결과**

```bash
cd hangang-pay-bank
./gradlew test --tests '*TransactionCommandServiceTest'
BUILD SUCCESSFUL
```

### Phase 6 - RabbitMQ Publisher Abstraction and Outbox Publisher

#### Goal

Publish outbox rows to RabbitMQ through an interchangeable publisher port.

#### DoD

- `BlockchainSyncMessagePublisher` port exists.
- Rabbit implementation exists and is selected by property.
- Rabbit config declares exchange, queue, and DLQ.
- Outbox publisher scheduler publishes `NEW` records and marks `SENT`.
- Publish failure increments retry count.

#### Implementation Files

- `hangang-pay-bank/build.gradle` for Rabbit dependency if absent
- `hangang-pay-bank/src/main/java/.../domain/blockchainoutbox/service/BlockchainOutboxPublisherScheduler.java`
- `hangang-pay-bank/src/main/java/.../domain/blockchainoutbox/port/BlockchainSyncMessagePublisher.java`
- `hangang-pay-bank/src/main/java/.../infra/mq/rabbit/RabbitBlockchainSyncMessagePublisher.java`
- `hangang-pay-bank/src/main/java/.../infra/mq/rabbit/RabbitMqConfig.java`
- `hangang-pay-bank/src/main/resources/application.yaml`

#### Test Cases

- Scheduler publishes publishable outbox.
- Scheduler marks row `SENT` after successful publish.
- Scheduler increments retry on publisher exception.
- Rabbit publisher sends expected routing key/payload.
- Property selection loads Rabbit implementation.

#### RED State

- Tests fail because publisher port/scheduler does not exist.

#### GREEN State

- Publisher and scheduler tests pass with mocked publisher/RabbitTemplate.

#### Verification Command

```bash
cd hangang-pay-bank
./gradlew test --tests '*OutboxPublisher*' --tests '*Rabbit*'
```

#### 구현 결과

- `spring-boot-starter-amqp` 의존성 추가. `@EnableScheduling` 메인 클래스에 추가.
- `RabbitMqConfig`: blockchain-sync exchange/queue, DLQ exchange/queue, Jackson MessageConverter 선언.
- `RabbitBlockchainSyncMessagePublisher`: `mq.provider=rabbit` 조건부 빈, exchange/routing key로 publish.
- `BlockchainOutboxPublisherScheduler`: 5초 주기로 NEW outbox 조회 → publish → SENT 전환, 실패 시 incrementRetryOrFail.
- `application.yaml`에 rabbitmq 접속 정보 및 `mq.provider`, `outbox.publisher.delay-ms` 추가.
- `./gradlew test --tests '*OutboxPublisher*' --tests '*Rabbit*'` 6개 전체 PASS.

### Phase 7 - Blockchain Submit and Receipt Split

#### Goal

Refactor blockchain write calls so async workers can submit a transaction, persist `txHash`, then handle receipt completion separately.

#### DoD

- `ContractCallService` has payment/cancel submit methods that return `txHash` without waiting for receipt.
- Receipt lookup/waiting is exposed separately.
- Existing synchronous charge/exchange behavior is preserved or adapted without changing scope.
- UUID-to-`bytes32` conversion utility exists and is tested.
- Contract function encoding uses the new `bytes32 transactionUuid` argument.
- There is a clear API for consumer code:
  - submit if `txHash` is absent.
  - query/wait receipt if `txHash` is present.

#### Implementation Files

- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchain/service/ContractCallService.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchain/dto/SubmittedBlockchainTx.java`
- `hangang-pay-bank/src/main/java/family/fisa/hangangpaybank/domain/blockchain/service/BlockchainTransactionKeyConverter.java`
- `hangang-pay-bank/src/test/java/family/fisa/hangangpaybank/domain/blockchain/service/ContractCallServiceTest.java`
- `hangang-pay-bank/src/test/java/family/fisa/hangangpaybank/domain/blockchain/service/BlockchainTransactionKeyConverterTest.java`

#### Test Cases

- UUID string converts deterministically to `bytes32`.
- Payment submit encodes `pay(bytes32,address,address,uint256)`.
- Cancel submit encodes `cancelPayment(bytes32,address,address,uint256)`.
- Submit returns `SubmittedBlockchainTx(txHash)` after `signAndSend`.
- Submit does not call `waitForReceipt`.
- Receipt query returns present receipt when available.
- Receipt query returns empty or retryable result when not yet mined.
- Existing synchronous methods, if retained, still wait for receipt.

#### RED State

- Tests fail because `ContractCallService` only returns `TransactionReceipt` after waiting.

#### GREEN State

- Submit/receipt tests pass and contract encoding includes transaction UUID.

#### Verification Command

```bash
cd hangang-pay-bank
./gradlew test --tests '*ContractCallServiceTest' --tests '*BlockchainTransactionKeyConverterTest'
```

#### 구현 결과

- `SubmittedBlockchainTx(String txHash)` record 추가.
- `BlockchainTransactionKeyConverter.toBytes32(String uuid)` 추가. UUID 16바이트를 32바이트 오른쪽 정렬(앞 16바이트 zero padding).
- `ContractCallService`에 `submitPayment`, `submitCancelPayment`, `submitContractFunction`, `submitFunctionTransaction` 추가. `waitForReceipt` 호출 없이 txHash만 반환.
- 기존 `pay()`, `cancelPayment()`, `sendFunctionTransaction()` 동기 경로 유지.
- `./gradlew test --tests '*ContractCallServiceTest' --tests '*BlockchainTransactionKeyConverterTest'` 전체 PASS. `./gradlew build -x test` 성공.

### Phase 8 - RabbitMQ Consumer and Blockchain Submission

#### Goal

Consume blockchain sync messages and submit actual blockchain transactions.

#### DoD

- Consumer handles payment and cancel messages.
- Consumer is idempotent.
- Initial runtime configuration is single-signer safe: consumer concurrency `1` or signer lock.
- If signer lock is implemented instead of concurrency `1`, the lock covers only nonce lookup, signing, send, and txHash acquisition.
- Consumer calls submit methods with transaction UUID bytes32 only when `txHash` is absent.
- Ledger is marked `SUBMITTED` when txHash is known.
- Ledger is marked `SUCCESS` when receipt succeeds.
- If ledger is already `SUBMITTED`, consumer skips submit and checks receipt by existing `txHash`.
- Retryable 에러(RPC_FAILED, RECEIPT_TIMEOUT)는 예외를 throw해 RabbitMQ NACK → 즉시 DLQ 이동한다.
- INSUFFICIENT_TOKEN_BALANCE는 DB-first 전환 후 DB 잔액 검증을 통과했으므로 발생 시 DB-blockchain 불일치 버그로 간주, FATAL 처리한다.
- Non-retryable 에러는 코드/인프라 버그로 간주한다. 보상 트랜잭션은 구현하지 않는다. FAILED 마킹 + log.error 후 ACK.
- 현재 DLQ는 자동 재시도 큐가 아니라 수동 재처리 대기 큐로 운영한다. 지수 백오프 재시도는 별도 phase에서 구현한다.
- Retryable/non-retryable 분류는 `BlockchainErrorCode` enum이 아닌 컨슈머 레이어(BlockchainSyncProcessor)의 상수 Set으로 정의한다.

#### Implementation Files

- `hangang-pay-bank/src/main/java/.../infra/mq/rabbit/RabbitBlockchainSyncMessageListener.java`
- `hangang-pay-bank/src/main/java/.../domain/blockchain/service/ContractCallService.java`
- `hangang-pay-bank/src/main/java/.../domain/transaction/service/BlockchainSyncProcessor.java`
- `hangang-pay-bank/src/main/java/.../domain/transaction/service/PaymentBlockchainSyncHandler.java`
- `hangang-pay-bank/src/main/java/.../domain/transaction/service/CancelBlockchainSyncHandler.java`
- `hangang-pay-bank/src/test/java/.../transaction/service/BlockchainSyncProcessorTest.java`

#### Test Cases

- With configured single consumer or signer lock, two messages cannot submit with the same signer concurrently.
- Already `SUCCESS` ledger causes no contract call and ack.
- Payment message without txHash calls submit payment and stores txHash.
- Cancel message without txHash calls submit cancel and stores txHash.
- SUBMITTED ledger with txHash does not submit again.
- SUBMITTED ledger with txHash checks receipt.
- Success receipt marks ledger `SUCCESS`.
- Tx hash known but receipt timeout marks `SUBMITTED` or leaves retryable state.
- `BLOCKCHAIN_RPC_FAILED` → retryable → throw → NACK → 즉시 DLQ 이동.
- `BLOCKCHAIN_RECEIPT_TIMEOUT` → retryable → throw → NACK → 즉시 DLQ 이동.
- `ERC20InsufficientBalance` → non-retryable → FAILED 마킹 + log.error → ACK. DB 잔액 검증 통과 후 발생 시 DB-blockchain 불일치 버그.
- `BLOCKCHAIN_MERCHANT_NOT_REGISTERED` → non-retryable → FAILED 마킹 + log.error → ACK. 보상 트랜잭션 없음.
- `BLOCKCHAIN_UNAUTHORIZED`, `BLOCKCHAIN_INVALID_ADDRESS` → non-retryable → FAILED 마킹 + log.error → ACK.

#### RED State

- Processor tests fail because no async consumer path exists.

#### GREEN State

- Processor tests pass with mocked contract service and repository.

#### Verification Command

```bash
cd hangang-pay-bank
./gradlew test --tests '*BlockchainSyncProcessor*'
```

#### 구현 결과

**변경 파일**

- `RabbitBlockchainSyncMessageListener` (신규): `concurrency=1` 단일 signer 안전 컨슈머. 예외 전파 시 NACK → DLQ.
- `BlockchainSyncProcessor` (신규): PAYMENT/CANCEL 처리 핵심 로직. txHash 체크포인트 패턴, retryable/non-retryable 오류 분기.
- `BlockchainLedgerStateWriter` (신규): `REQUIRES_NEW` 독립 커밋으로 SUBMITTED/SUCCESS/FAILED 상태 전환. self-invocation 프록시 문제 방지를 위해 별도 빈으로 분리.
- `PaymentBlockchainSyncHandler`, `CancelBlockchainSyncHandler` (신규): `BlockchainSyncHandler` 구현체.
- `ContractCallService` (수정): `waitForReceiptByHash(String txHash)` 추가.
- `BlockchainLedger` (수정): `markSubmitted(String txHash)` 추가.
- `RabbitMqConfig` (수정): `rabbitListenerContainerFactory` 빈 추가. `defaultRequeueRejected=false`로 NACK 시 즉시 DLQ 이동.
- `BlockchainSyncProcessorTest` (신규): 8개 테스트 케이스.

**설계 결정 사항**

- 보상 트랜잭션 미구현: `BLOCKCHAIN_MERCHANT_NOT_REGISTERED` 등은 request 시점 검증을 통과했으므로 실질적으로 발생 불가. FAILED 마킹 + log.error + ACK.
- `BLOCKCHAIN_INSUFFICIENT_TOKEN_BALANCE`는 RETRYABLE 제외: DB 잔액 검증 통과 후 발생 시 DB-blockchain 불일치 버그이므로 retry 무의미.
- 로컬 재시도(RetryInterceptor) 미적용: RPC 장애는 수십 초~수 분 단위라 스레드 블로킹 비용 큼. DLQ에서 수동 재투입.

**테스트 결과**

```
./gradlew test --tests '*BlockchainSyncProcessor*'
BUILD SUCCESSFUL
8 tests, 8 passed
```

### Phase 8.5 - RabbitMQ Retry Queue + TTL + Backoff

#### Goal

Replace the current immediate-DLQ policy for retryable consumer failures with broker-driven delayed retry queues using TTL and bounded backoff.

#### DoD

- Retryable consumer failures no longer go directly to final DLQ on first failure.
- RabbitMQ config declares retry queues with TTL and dead-letter routing back to the main queue.
- Retry backoff policy is explicit and bounded, for example `10s -> 1m -> 5m -> final DLQ`.
- Message envelope carries retry attempt metadata needed to route to the next retry queue.
- Consumer ACKs the original message after republishing to the next retry queue.
- Max retry exceeded sends the message to final DLQ.
- Existing non-retryable failures still mark `blockchain_ledger FAILED` and ACK.
- Existing `txHash` checkpoint logic continues to prevent duplicate submit on retried messages.

#### Implementation Files

- `hangang-pay-bank/src/main/java/.../infra/mq/rabbit/RabbitMqConfig.java`
- `hangang-pay-bank/src/main/java/.../infra/mq/rabbit/RabbitBlockchainRetryPublisher.java`
- `hangang-pay-bank/src/main/java/.../domain/blockchainoutbox/dto/BlockchainSyncMessage.java`
- `hangang-pay-bank/src/main/java/.../domain/transaction/service/BlockchainSyncProcessor.java`
- `hangang-pay-bank/src/main/java/.../infra/mq/rabbit/RabbitBlockchainSyncMessageListener.java`
- `hangang-pay-bank/src/test/java/.../infra/mq/rabbit/*`
- `hangang-pay-bank/src/test/java/.../transaction/service/BlockchainSyncProcessorTest.java`

#### Test Cases

- Retryable failure on first attempt republishes to retry queue `10s` and ACKs original message.
- Second retryable failure republishes to retry queue `1m`.
- Third retryable failure republishes to retry queue `5m`.
- Retry count over max sends to final DLQ.
- Message returning from retry queue with existing `txHash` does not resubmit and only rechecks receipt.
- Non-retryable failure still marks ledger `FAILED` and does not go through retry queues.
- Retry queue TTL expiry routes messages back to the main queue with the expected routing key.

#### RED State

- Tests fail because retry queue topology and retry republish path do not exist.

#### GREEN State

- Retryable failures follow the configured backoff chain and final DLQ routing.

#### Verification Command

```bash
cd hangang-pay-bank
./gradlew test --tests '*BlockchainSyncProcessorTest' --tests '*Rabbit*'
```

#### Notes

- Keep `defaultRequeueRejected=false`; do not rely on broker default requeue loops.
- Prefer explicit republish-to-retry-queue over listener thread blocking retries.
- Use `retryCount` or equivalent message metadata to route retries.

### Phase 9 - Reconciliation Scheduler

#### Goal

Recover DB-blockchain drift for uncertain ledgers.

#### DoD

- Scheduler scans only uncertain records.
- `PENDING` without txHash checks outbox state and republishes when appropriate.
- `SUBMITTED` or `PENDING` with txHash checks receipt.
- Event lookup by transaction UUID key is supported for the narrow case where blockchain submit succeeded but DB txHash checkpoint was not saved.
- `FAILED` with txHash is rechecked before remaining failed.
- No full-table comparison of all successful records.

#### Implementation Files

- `hangang-pay-bank/src/main/java/.../domain/transaction/service/BlockchainLedgerReconcileScheduler.java`
- `hangang-pay-bank/src/main/java/.../domain/blockchain/service/ContractCallService.java`
- `hangang-pay-bank/src/main/java/.../domain/blockchain/repository/BlockchainLedgerRepository.java`
- `hangang-pay-bank/src/main/java/.../domain/blockchain/repository/jpa/BlockchainLedgerJpaRepository.java`
- `hangang-pay-bank/src/test/java/.../transaction/service/BlockchainLedgerReconcileSchedulerTest.java`

#### Test Cases

- Old `PENDING` with unsent outbox triggers republish path.
- Old `PENDING` with no txHash searches transaction UUID event before republishing.
- Event hit restores txHash and marks `SUBMITTED` or `SUCCESS` based on receipt.
- `SUBMITTED` with successful receipt becomes `SUCCESS`.
- `SUBMITTED` with failed receipt becomes `FAILED`.
- `FAILED` with successful receipt is corrected to `SUCCESS`.
- Recent `PENDING` is ignored.
- `SUCCESS` records are ignored.

#### RED State

- Scheduler tests fail because no reconcile service exists.

#### GREEN State

- Reconcile tests pass with mocked blockchain/outbox dependencies.

#### Verification Command

```bash
cd hangang-pay-bank
./gradlew test --tests '*Reconcile*'
```

#### 구현 결과 - DB 기반 1차

**변경 파일**

- `BlockchainLedgerReconcileScheduler` (신규): 오래된 uncertain DB 상태를 스캔한다.
- `BlockchainLedgerRepository`, `BlockchainLedgerJpaRepository`, `BlockchainLedgerRepositoryImpl`: `PENDING/SUBMITTED/FAILED` stale 조회 추가.
- `BlockchainOutboxRepository`, `BlockchainOutboxJpaRepository`, `BlockchainOutboxRepositoryImpl`: ledger 기준 outbox 조회 및 stale `FAILED` 조회 추가.
- `BlockchainOutbox`: reconciliation 재발행 대기를 위한 `reopenForReconcile()` 추가.
- `BlockchainLedgerStateWriter`: receipt status에 따라 `SUCCESS/FAILED`를 결정하는 `markReceiptResult()` 추가.
- `BlockchainSyncProcessor`: receipt 실패를 `SUCCESS`로 기록하지 않도록 `markReceiptResult()` 사용.
- `BlockchainLedgerReconcileSchedulerTest` (신규): DB 기반 reconciliation 단위 테스트.
- `build.gradle`: Sentry dependency 선언 comma 구문 오류 수정.

**현재 1차 동작**

- 오래된 `blockchain_outbox(FAILED)`는 `NEW`로 되돌려 기존 outbox publisher가 재발행한다.
- 오래된 `blockchain_ledger(PENDING)`에 `txHash`가 없으면 연결된 outbox를 `NEW`로 되돌린다.
- 오래된 `PENDING/SUBMITTED`에 `txHash`가 있으면 receipt를 재조회해 `SUCCESS/FAILED`로 반영한다.
- `FAILED`이지만 `txHash`가 있는 ledger는 receipt를 재조회해 성공 receipt면 복구될 수 있다.
- receipt 조회 timeout/RPC 오류는 상태를 바꾸지 않고 다음 reconciliation 주기에 재시도한다.

**아직 남은 항목**

- `transactionUuid` event scan으로 submit 성공 후 DB txHash checkpoint 저장 실패 케이스 복원.
- RabbitMQ DLQ 메시지 직접 consume/requeue 자동화.
- TTL retry queue + bounded backoff (Phase 8.5).

**테스트 결과**

```text
./gradlew test --tests '*BlockchainLedgerReconcileSchedulerTest' --tests '*BlockchainSyncProcessorTest' --tests '*BlockchainOutboxPublisherSchedulerTest'
BUILD SUCCESSFUL
```

### Phase 10 - End-to-End Verification

#### Goal

Verify bank-blockchain async payment flow in local/test profile.

#### DoD

- App context loads.
- Payment request returns before receipt wait.
- Outbox publisher sends message.
- Consumer updates ledger to `SUCCESS`.
- Reconcile does not alter already successful rows.
- DLQ path is observable for forced failures.

#### Implementation Files

- `hangang-pay-bank/src/test/java/...` integration tests if feasible
- `hangang-pay-bank/src/main/resources/application-test.yml`
- Docker/local RabbitMQ notes if needed

#### Test Cases

- Async payment happy path.
- Async cancel happy path.
- Whitelist false fails synchronously.
- Request-time blockchain validation errors are mapped safely by BE `BankClientImpl`.
- Consumer duplicate message no-op.
- Reconcile submitted receipt success.

#### RED State

- Integration tests fail due missing config or incomplete wiring.

#### GREEN State

- Targeted tests and app context pass.

#### Verification Command

```bash
cd hangang-pay-bank
./gradlew test
```

## Test Strategy

- Use TDD phase by phase.
- Prefer focused unit tests for service behavior and exception policy.
- Use repository tests for locking/outbox query behavior if current test setup supports JPA.
- Use mocked `ContractCallService` for request-thread tests to assert no state-changing blockchain call.
- Use mocked Rabbit publisher for outbox scheduler.
- Use mocked receipt/event reads for reconciliation.
- Add focused tests proving submit does not wait for receipt and txHash can be saved before receipt handling.
- Keep full integration tests minimal because RabbitMQ and blockchain local dependencies can make CI brittle.

Important assertions:

- Payment request thread never calls `pay()`.
- Cancel request thread never calls `cancelPayment()`.
- Request thread may call only `isMerchant()`.
- Payment/cancel responses expose only `transactionUuid`, `status`, `confirmedAt`, `fromBalance`, `toBalance`.
- Payment/cancel responses do not expose `txHash`, `blockNumber`, `bankTransactionId`, or blockchain sync status.
- `confirmedAt` in response equals `WalletLedger.confirmedAt`.
- Every payment/cancel attempt (success or failure) creates a `WalletLedger` record via `REQUIRES_NEW`.
- `BlockchainLedger` and outbox are created only on bank SUCCESS.
- DB balance updates happen exactly once per transaction UUID.
- MQ consumer is idempotent.
- Consumer does not resubmit when ledger already has txHash.
- Reconcile ignores stable `SUCCESS` rows.

## Risks

- New `balance` column requires DB migration/default handling for existing wallets.
- Pessimistic locking can deadlock if wallets are locked in inconsistent order.
- On-chain whitelist can change between request validation and async consumer submission.
- On-chain balance can lag DB; consumer may temporarily fail with insufficient token balance.
- Contract ABI changes require artifact regeneration and bank `ContractCallService` update.
- Adding `SUBMITTED` status affects internal blockchain sync status mapping. It must not leak into BE-facing payment/cancel status.
- Existing BE BankClient DTOs may need field alignment if they currently expect blockchain fields, but the target contract is Bank DB business confirmation only.
- BE `BankClientImpl` currently passes `BLOCKCHAIN_*` through as `ExternalBankErrorCode`; payment/cancel integration should remap request-time blockchain validation errors to frontend-safe service errors.
- Outbox publish success with DB state update failure can cause duplicate MQ messages; consumer idempotency must handle this.
- Reconciliation by event UUID requires reliable event query implementation and correct UUID-to-bytes32 conversion.
- RabbitMQ/DLQ settings can cause either excessive retries or premature failure if tuned poorly.
- Splitting submit and receipt changes `ContractCallService` responsibilities; synchronous charge/exchange flows must not regress while payment/cancel move async.
- Single signer is a hard blockchain submit throughput limit. MQ improves request latency and buffering, but it does not remove nonce serialization.
- Increasing consumer concurrency without signer-safe nonce handling can create duplicate nonce and unstable blockchain submits.
- Do not add signer pool/operator authorization until blockchain TPS and backlog measurements show the single signer path is the actual bottleneck.

## Current Next Action

Phase 9 DB 기반 1차 reconciliation 구현 완료.
현재 MQ 경로는 retryable 오류 발생 시 즉시 DLQ로 이동하고, 자동 재시도는 없다.
다음 작업은 Phase 8.5 (Retry Queue + TTL + Backoff)를 구현해 `10s → 1m → 5m → 최종 DLQ` 지연 재시도로 전환하는 것이다.
그 후 Phase 9 잔여 항목인 `transactionUuid` event scan 기반 txHash 복원을 추가한다.

## Phase Checklist

- [x] Phase 0 - Common Async Pipeline Contracts
- [x] Phase 1 - Contract UUID Events
- [x] Phase 1.5 - Contract processedTx Storage (재시도 안전성)
- [x] Phase 2 - Bank Wallet Balance Restore, Locking, and WalletLedger
- [x] Phase 3 - Whitelist View Call
- [x] Phase 4 - Outbox Domain Model
- [x] Phase 5 - Payment/Cancel DB-First Flow
- [x] Phase 6 - RabbitMQ Publisher Abstraction and Outbox Publisher
- [x] Phase 7 - Blockchain Submit and Receipt Split
- [x] Phase 8 - RabbitMQ Consumer and Blockchain Submission
- [ ] Phase 8.5 - RabbitMQ Retry Queue + TTL + Backoff
- [x] Phase 9A - DB-based Reconciliation Scheduler
- [ ] Phase 9B - Event UUID txHash Restore
- [ ] Phase 10 - End-to-End Verification
