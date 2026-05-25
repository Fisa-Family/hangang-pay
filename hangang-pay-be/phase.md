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
- Add `PaymentControllerTest` cases for session-based controller contracts.
- Verify targeted tests fail for the expected missing implementation before writing GREEN code.

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
  - Bank `SUCCESS` updates local transaction to `SUCCESS`.
  - Bank `FAILED` updates local transaction to `FAILED`.
  - Bank `PROCESSING`, `PENDING`, or unresolved status keeps the local transaction recoverable.

## Phase 6: Docs and Verification

- Update `docs/rest_api.md` when API path, request, response, or role rules change.
- Keep state, Redis safety, and Bank lookup contract summarized here for future sessions.
- Verification commands:
  - `./gradlew test --tests family.fisa.hangangpay.domain.transaction.service.TransactionCommandServiceTest`
  - `./gradlew test --tests family.fisa.hangangpay.domain.transaction.controller.PaymentControllerTest`
  - `./gradlew test`

## Assumptions

- Payment intent TTL is 10 minutes.
- Redis idempotency record TTL is 7 days.
- The client only sends back the server-issued `transactionUuid` after intent creation.
- The client may use a UI-only `clientRequestId`, but it is not used for payment consistency.
