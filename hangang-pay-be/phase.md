# Payment API Phase Plan

## Phase 0: Intake

- Goal: Implement payment APIs based on server-generated `transactionUuid` and server-generated `requestHash`.
- DoD:
  - Payment intent creation works.
  - Payment execution works.
  - `UNKNOWN` recovery works.
  - Redis idempotency, lock, and rate limit safety paths are in place.
  - Bank transaction status lookup contract is represented in `BankClient`.
  - Targeted and full tests pass.
- Forbidden:
  - Do not use a client-generated idempotency key for payment consistency.
  - Do not include `paymentPin` in `requestHash` inputs or Redis response snapshots.

## Phase 1: RED Tests

- Add `TransactionCommandServiceTest` cases for intent, execute, and recover behavior first.
- `PaymentControllerTest` is intentionally excluded for now. The current learning pass focuses only on `TransactionCommandServiceTest`.
- Verify targeted tests fail for the expected missing implementation before writing GREEN code.

### Current Phase 1 Handoff State

- A `TransactionCommandServiceTest` has been written for these scenarios:
  - Payment intent creation stores a `PENDING` `PAYMENT` transaction.
  - Successful payment execution generates a server-side request hash and transitions through `PENDING -> PROCESSING -> SUCCESS`.
  - Bank timeout transitions through `PENDING -> PROCESSING -> UNKNOWN`.
  - Same `transactionUuid` + same `requestHash` retry returns the stored response snapshot and does not call Bank again.
  - Same `transactionUuid` + different `requestHash` fails with `IDEMPOTENCY_CONFLICT`.
  - `UNKNOWN` recovery updates local status from Bank lookup result.
- The redundant standalone test named like `executePayment_generatesRequestHashOnServer` should stay removed; the success test already verifies hash generation and idempotency-store handoff.
- The latest targeted RED command was:
  - `./gradlew test --tests family.fisa.hangangpay.domain.transaction.service.TransactionCommandServiceTest`
- The latest RED is good and expected. It fails in `compileTestJava` because the production implementation is intentionally incomplete:
  - `TransactionCommandService` has no constructor for the new dependencies.
  - `createPaymentIntent(...)` does not exist.
  - `executePayment(...)` does not exist.
  - `recoverPayment(...)` does not exist.
  - `BankClient.getTransactionStatus(String)` does not exist.
  - `TransactionErrorCode.IDEMPOTENCY_CONFLICT` does not exist.
- Do not treat this RED as a broken setup. It is the intended handoff point into Phase 2.

### Current Helper Component Structure

- Use these package roles:
  - `domain/transaction/service`: use-case entry points, especially `TransactionCommandService` and `TransactionQueryService`.
  - `domain/transaction/internal`: transaction-domain internal contracts and pure helpers used by `TransactionCommandService`.
  - `domain/transaction/infra/redis`: Redis implementations of the internal contracts.
- Current internal contracts/helpers:
  - `PaymentIdempotencyStore`
  - `PaymentIdempotencyDecision`
  - `PaymentIdempotencyDecisionType`
  - `PaymentLockManager`
  - `PaymentRateLimiter`
  - `PaymentRequestHashGenerator`
- Current Redis implementation shells:
  - `RedisPaymentIdempotencyStore`
  - `RedisPaymentLockManager`
  - `RedisPaymentRateLimiter`
- `PaymentRequestHashGenerator` is a normal class, not an interface. It is a pure helper and can be mocked in tests or used directly later.
- `PaymentIdempotencyStore`, `PaymentLockManager`, and `PaymentRateLimiter` should stay interfaces because their implementation is Redis-backed and easier to mock behind contracts.

### Learning Mode Rule

- Do not auto-generate code in the repository unless the user explicitly asks for file edits.
- When continuing, show code snippets and let the user type them manually.
- Prefer one phase at a time; do not jump ahead to full payment execution while Phase 2 intent is still RED/GREEN.

## Phase 2: Payment Intent

- Endpoint: `POST /api/v1/payment/intents`
- Request: `merchantPartyId`, `amount`, `itemName`
- Behavior:
  - Validate the consumer session party.
  - Validate the merchant party.
  - Resolve both wallets.
  - Generate `transactionUuid` on the platform server.
  - Persist `Transaction(PAYMENT, PENDING)`.
- Response:
  - `transactionUuid`
  - `status`
  - `merchantPartyId`
  - `merchantName`
  - `amount`
  - `itemName`
  - `expiresAt`

### Phase 2 Next Step

- Make only the first service test GREEN first:
  - `createPaymentIntent_savesPendingPaymentTransaction`
- Minimal production changes for Phase 2:
  - Add final dependency fields to `TransactionCommandService` so Lombok `@RequiredArgsConstructor` creates the constructor expected by the test.
  - Add `TransactionErrorCode.IDEMPOTENCY_CONFLICT` only if needed to keep the test class compiling before execution tests are implemented.
  - Add `BankClient.getTransactionStatus(String)` declaration only if needed to keep the test class compiling before recovery is implemented.
  - Implement `createPaymentIntent(Long partyId, PaymentIntentCreateRequest request)`.
  - Add temporary compile-only stubs for `executePayment(...)` and `recoverPayment(...)` if the test class cannot compile without them. Do not implement their logic in Phase 2.
- `createPaymentIntent(...)` expected flow:
  - `paymentRateLimiter.checkIntentRateLimit(partyId, request.merchantPartyId())`
  - Load user `Party` by `partyRepository.findById(partyId)`.
  - Load merchant by `merchantRepository.findByParty_Id(request.merchantPartyId())`.
  - Load consumer wallet by `walletRepository.findByParty_Id(partyId)`.
  - Load merchant wallet by `walletRepository.findByParty_Id(request.merchantPartyId())`.
  - Generate `UUID.randomUUID().toString()` on the server.
  - Create `Transaction.forPayment(...)` with `status=PENDING`.
  - Save via `transactionRepository.save(...)`.
  - Return `PaymentIntentResponse`.
- Keep execute/recover RED after Phase 2. Phase 2 is done when the intent test passes and remaining failures are only execute/recover-related.

## Phase 3: Payment Execute

- Endpoint: `POST /api/v1/payment/{transactionUuid}/execute`
- Request: `paymentPin`
- Behavior:
  - Load the stored transaction by `transactionUuid`.
  - Validate owner, payment status, expiration, and payment PIN.
  - Generate `requestHash` from stored server-side transaction data.
  - Call Bank `payment(...)` only after Redis lock, idempotency, and rate limit checks pass.
  - State transition: `PENDING -> PROCESSING -> SUCCESS | FAILED | UNKNOWN`.
- `requestHash` input fields:
  - `method`
  - `endpoint`
  - `transactionUuid`
  - `fromPartyId`
  - `merchantPartyId`
  - `amount`

## Phase 4: Redis Safety

### Phase 4 Handoff State

- Phase 3 payment execute success path is implemented and committed before this handoff.
- Current execute architecture:
  - `TransactionCommandService.executePayment(...)` is the non-transactional orchestrator.
  - `executePayment(...)` should stay outside a DB transaction while Bank `payment(...)` is called.
  - `PaymentLockManager.withTransactionLock(...)` wraps the full execute flow with `payment:lock:{transactionUuid}`.
  - `PaymentExecutionStateWriter` owns short `REQUIRES_NEW` DB state transitions.
  - `PaymentExecutionPrepared` is the detached snapshot used for the Bank request outside the DB transaction.
- Phase 3 state boundary:
  - `prepareExecution(...)`: validates owner/status/PIN, creates server-side `requestHash`, begins idempotency, rate-limits, and commits `PROCESSING`.
  - Bank `payment(...)`: runs outside DB transaction while Redis lock is held.
  - `completeSuccess(...)`: commits Bank result and `SUCCESS`.
  - `markUnknown(...)`: commits `UNKNOWN` after Bank timeout/uncertain failure.
- Start Phase 4 by moving idempotency-specific execute tests to the component that owns the decision:
  - `same transactionUuid + same requestHash` returning a stored snapshot.
  - `same transactionUuid + different requestHash` throwing `IDEMPOTENCY_CONFLICT`.
  - duplicate in-flight execution throwing `PAYMENT_ALREADY_PROCESSING`.
- Keep `paymentPin` out of `requestHash`, Redis idempotency values, response snapshots, and logs.
- Do not broaden into recovery polling/scheduler behavior here; that belongs to Phase 5/6.

- Lock key: `payment:lock:{transactionUuid}`
- Idempotency key: `payment:idempotency:{transactionUuid}`
- Idempotency value:
  - `transactionUuid`
  - `requestHash`
  - `status`
  - `transactionId`
  - `responseSnapshot`
- Rules:
  - Same `transactionUuid` and same `requestHash` with completed snapshot returns the snapshot.
  - Same `transactionUuid` with different `requestHash` fails with `IDEMPOTENCY_CONFLICT`.
  - Processing duplicate requests fail fast with `PAYMENT_ALREADY_PROCESSING`.
- Rate limit keys:
  - `payment:rate:user:{partyId}`
  - `payment:rate:merchant:{merchantPartyId}`
  - `payment:rate:bank-outbound`

## Phase 5: UNKNOWN Recovery

- Endpoint: `POST /api/v1/payment/{transactionUuid}/recover`
- Bank contract: `GET /api/v1/transactions/{transactionUuid}`
- Behavior:
  - Only `UNKNOWN` or `PROCESSING` transactions are recoverable.
  - Local `PENDING` is not recoverable because the Bank payment request has not been sent yet.
  - Bank `SUCCESS` updates local transaction to `SUCCESS`.
  - Bank `FAILED` updates local transaction to `FAILED`.
  - Bank `PROCESSING`, `PENDING`, or unresolved status keeps the local transaction in a recoverable state.
  - Bank `SUCCESS` without `txHash` or `blockNumber` fails with `PAYMENT_RECOVERY_RESULT_INVALID`.
  - Non-recoverable local states fail with `PAYMENT_NOT_RECOVERABLE`.
  - Recovery runs under the same `payment:lock:{transactionUuid}` Redis lock used by execute.

### Phase 5 Completion State

- `TransactionCommandService.recoverPayment(...)` is implemented.
- `PaymentController` exposes `POST /api/v1/payment/{transactionUuid}/recover`.
- `PaymentController` also exposes the payment intent and execute endpoints:
  - `POST /api/v1/payment/intents`
  - `POST /api/v1/payment/{transactionUuid}/execute`
- Recovery service tests cover:
  - Bank `SUCCESS` -> local `SUCCESS`
  - Bank `FAILED` -> local `FAILED`
  - Bank still `PROCESSING` -> local recoverable state is preserved
  - local `PENDING` and final states are rejected as not recoverable
  - Bank `SUCCESS` with missing `txHash` or `blockNumber` fails as invalid recovery result
  - recovery uses the transaction Redis lock
- `PaymentControllerTest` is intentionally not added in this learning pass per current scope.

## Phase 6: Docs and Verification

- Update `docs/rest_api.md` when API path, request, response, or role rules change.
- Keep state, Redis safety, and Bank lookup contract summarized here for future sessions.
- UNKNOWN UX / recovery policy:
  - If `execute` returns `UNKNOWN`, the client must not treat it as a final failure.
  - The client should show a "payment confirmation in progress" state and poll payment status.
  - The backend may run a scheduler that treats `UNKNOWN` or long-running `PROCESSING` `PAYMENT` transactions as recovery targets.
  - Recovery must acquire the Redis lock again by `transactionUuid` before calling the Bank status lookup.
  - Bank `SUCCESS` finalizes the local transaction as `SUCCESS`; Bank `FAILED` finalizes it as `FAILED`.
  - Bank `PENDING`, `PROCESSING`, or unresolved results should leave the local transaction recoverable.
  - Local `PENDING` means the payment intent exists but Bank execution has not started, so it is not a recovery target.
- Verification commands:
  - `./gradlew test --tests family.fisa.hangangpay.domain.transaction.service.TransactionCommandServiceTest`
  - `./gradlew test`

## Assumptions

- Payment intent TTL is 10 minutes.
- Redis idempotency record TTL is 7 days.
- The client only sends back the server-issued `transactionUuid` after intent creation.
- The client may use a UI-only `clientRequestId`, but it is not used for payment consistency.
