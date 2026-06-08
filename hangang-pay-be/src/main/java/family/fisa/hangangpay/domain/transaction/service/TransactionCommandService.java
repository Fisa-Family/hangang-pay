package family.fisa.hangangpay.domain.transaction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.BankTransactionStatusResponse;
import family.fisa.hangangpay.client.bank.dto.CancelResponse;
import family.fisa.hangangpay.client.bank.dto.PaymentResponse;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.BankErrorBody;
import family.fisa.hangangpay.domain.transaction.dto.BankOutcome;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentCancelRequest;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentExecutionResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.cancel.*;
import family.fisa.hangangpay.domain.transaction.internal.payment.*;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.cancel.CancelExecutionStateWriter;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentExecutionStateWriter;
import family.fisa.hangangpay.domain.wallet.code.error.WalletErrorCode;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionCommandService {
    private static final long PAYMENT_INTENT_TTL_MINUTES = 10L;
    private static final long BANK_RETRY_DELAY_MILLIS = 200L;
    private static final Set<String> RETRYABLE_BANK_CODES =
            Set.of("TRANSACTION_DUPLICATE_PROCESSING");
    private static final Map<String, BaseErrorCode> BANK_FAIL_CODE_MAP =
            Map.of(
                    "TRANSACTION_INSUFFICIENT_BALANCE",
                            TransactionErrorCode.PAYMENT_INSUFFICIENT_BALANCE,
                    "TRANSACTION_ALREADY_FAILED", TransactionErrorCode.PAYMENT_ALREADY_FAILED);
    private static final Map<String, BaseErrorCode> CANCEL_BANK_FAIL_CODE_MAP =
            Map.of("TRANSACTION_ALREADY_FAILED", TransactionErrorCode.CANCEL_ALREADY_FAILED);

    private static final ObjectMapper BANK_ERROR_MAPPER = new ObjectMapper();

    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final WalletRepository walletRepository;
    private final PartyRepository partyRepository;
    private final BankClient bankClient;
    private final PaymentIdempotencyStore paymentIdempotencyStore;
    private final PaymentLockManager paymentLockManager;
    private final PaymentRateLimiter paymentRateLimiter;
    private final CancelIdempotencyStore cancelIdempotencyStore;
    private final CancelLockManager cancelLockManager;
    private final PaymentExecutionStateWriter paymentExecutionStateWriter;
    private final CancelExecutionStateWriter cancelExecutionStateWriter;
    private final CancelRequestHashGenerator cancelRequestHashGenerator;

    @Transactional
    public PaymentIntentResponse createPaymentIntent(
            Long partyId, PaymentIntentCreateRequest request) {

        /** 요청을 처리하기 전, TokenBucket 방식을 이용하여 Quota 확인 */
        paymentRateLimiter.checkIntentRateLimit(partyId, request.merchantPartyId());

        /** DB 조회 */
        Party userParty = getParty(partyId);
        Merchant merchant = getMerchant(request.merchantPartyId());
        Wallet userWallet = getWallet(partyId);
        Wallet merchantWallet = getWallet(request.merchantPartyId());

        /** 결제 실행 전에 서버 발급 transactionUuid로 PENDING 결제 의도를 생성한다 */
        String transactionUuid = UUID.randomUUID().toString();

        Transaction transaction =
                Transaction.forPayment(
                        transactionUuid,
                        userParty,
                        merchant.getParty(),
                        userWallet,
                        merchantWallet,
                        request.amount(),
                        null,
                        request.itemName());

        Transaction saved = transactionRepository.save(transaction);

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(PAYMENT_INTENT_TTL_MINUTES);

        return PaymentIntentResponse.from(saved, merchant, expiresAt);
    }

    /** Propagation.NOT_SUPPORTED: 트랜잭션 없이 실행 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentExecutionResponse executePayment(
            Long userId, Long partyId, String transactionUuid, PaymentExecuteRequest request) {

        return paymentLockManager.withTransactionLock(
                transactionUuid,
                () ->
                        doExecutePayment(
                                userId, partyId, transactionUuid, request)); // 콜백으로 락 걸고 이어서 수행
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentExecutionResponse recoverPayment(Long partyId, String transactionUuid) {
        return paymentLockManager.withTransactionLock(
                transactionUuid, () -> doRecoverPayment(partyId, transactionUuid));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentCancelResponse executeCancel(
            Long merchantPartyId, Long transactionId, PaymentCancelRequest request) {

        /** 1. Lock 확보 - cancelUuid는 prepareCancel 내부에서 생성됨, originalTrnasacitonUuid 사용 */
        String originalTransactionUuid = getTransactionUuid(transactionId);

        /** 2. 분산 락 - 같은 originalPaymentUuid 취소가 동시에 두 건 진입하지 못하게 차단 */
        return cancelLockManager.withCancelLock(
                originalTransactionUuid,
                () ->
                        doExecuteCancel(
                                merchantPartyId, transactionId, originalTransactionUuid, request));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentCancelResponse recoverCancel(Long merchantPartyId, Long transactionId) {
        String originalTransactionUuid = getTransactionUuid(transactionId);

        return cancelLockManager.withCancelLock(
                originalTransactionUuid, () -> doRecoverCancel(merchantPartyId, transactionId));
    }

    /** 내부 메소드 */
    private PaymentExecutionResponse doExecutePayment(
            Long userId, Long partyId, String transactionUuid, PaymentExecuteRequest request) {

        /** 1. 거래 조회/검증, PROCESSING 저장 */
        PaymentExecutionPreparationResult result =
                paymentExecutionStateWriter.prepareExecution(
                        userId, partyId, transactionUuid, request.paymentPin());

        if (result.hasSnapshot()) {
            return result.responseSnapshot();
        }

        PaymentExecutionPrepared prepared = result.prepared();

        /** 2. Bank 외부 호출 */
        BankOutcome<PaymentResponse> outcome =
                callBankWithRetry(
                        () -> bankClient.payment(prepared.toBankPaymentRequest()),
                        BANK_FAIL_CODE_MAP,
                        TransactionErrorCode.PAYMENT_FAILED);

        /** 3. 결과 분류 및 상태 반영 */
        PaymentExecutionResponse response =
                switch (outcome.type()) {
                    case SUCCESS ->
                            paymentExecutionStateWriter.completeSuccess(
                                    transactionUuid,
                                    outcome.value().txHash(),
                                    String.valueOf(outcome.value().bankTransactionId()),
                                    outcome.value().confirmedAt());
                    case UNKNOWN -> paymentExecutionStateWriter.markUnknown(transactionUuid);
                    case TERMINAL_FAILED -> {
                        paymentExecutionStateWriter.completeFailed(transactionUuid);
                        throw new BusinessException(
                                outcome.errorCode()); // GlobalExceptionHandler 감지
                    }
                };

        /** 4. Redis용 idempotency snapshot 저장 */
        paymentIdempotencyStore.completeExecution(transactionUuid, response);

        return response;
    }

    private PaymentExecutionResponse doRecoverPayment(Long partyId, String transactionUuid) {
        // 1. 복구 대상 검증 + 복구용 uuid 확보 (UNKNOWN / PROCESSING)
        String recoveryUuid = paymentExecutionStateWriter.prepareRecovery(partyId, transactionUuid);

        // 2. Bank 조회로 결과 확정 (404은 은행 미도달로 간주 -> FAILED 처리)
        PaymentExecutionResponse response = resolvePaymentRecovery(recoveryUuid);

        // 3. 종단으로 끝났다면, Redis 멱등 record도 정리한다. -> 고아 상태인 PROCESSING 청소
        if (response.status() == TransactionStatus.SUCCESS) {
            paymentIdempotencyStore.completeExecution(recoveryUuid, response);
        } else if (response.status() == TransactionStatus.FAILED) {
            paymentIdempotencyStore.failExecution(recoveryUuid);
        } else {
            // 은행이 아직 처리 중 → 시도 횟수만 올리고 다음 주기 재시도 (cap 도달 시 sweep 제외)
            paymentExecutionStateWriter.incrementRecoveryAttempt(recoveryUuid);
        }

        return response;
    }

    /**
     * bankClient 호출 전 종료된 요청들은 PROCESSING 레코드가 저장되고 고아상태에 빠진다. 이런 경우는 은행쪽에 조회 응답이 404 - NOT FOUND로
     * 반환 된다.
     */
    private PaymentExecutionResponse resolvePaymentRecovery(String recoveryUuid) {
        try {
            // 1. 정상 조회: SUCCESS/FAILED/PROCESSING을 applyRecoveryResult가 반영한다.
            BankTransactionStatusResponse bankStatus =
                    bankClient.getTransactionStatus(recoveryUuid);
            return paymentExecutionStateWriter.applyRecoveryResult(recoveryUuid, bankStatus);
        } catch (RestClientResponseException e) {
            // 2. 404가 아니면 (5xx 등) 일시적 오류 같은 경우 다시 던져서 다음 sweep에 재시도한다.
            if (!e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw e;
            }
            // 3. 404는 은행 DB 원장에 기록자체가 없다. -> 은행 도달전 사망했다는 의미로 FAILED 확정 (플랫폼의 책임)
            // applyRecoveryResult의 FIALED 처리를 그대로 재사용하기 위해 FAILED status 합성
            BankTransactionStatusResponse asFailed =
                    BankTransactionStatusResponse.failed(recoveryUuid);

            return paymentExecutionStateWriter.applyRecoveryResult(recoveryUuid, asFailed);
        }
    }

    private PaymentCancelResponse doExecuteCancel(
            Long merchantPartyId,
            Long transactionId,
            String originalTransactionUuid,
            PaymentCancelRequest request) {
        /** 1. 멱등성 판정 */
        String requestHash =
                cancelRequestHashGenerator.generate(originalTransactionUuid, merchantPartyId);

        CancelIdempotencyDecision decision =
                cancelIdempotencyStore.beginCancel(originalTransactionUuid, requestHash);

        if (decision.type() == CancelIdempotencyDecisionType.RETURN_SNAPSHOT) {
            return decision.responseSnapshot();
        }

        if (decision.type() == CancelIdempotencyDecisionType.ALREADY_FAILED) {
            throw new BusinessException(TransactionErrorCode.CANCEL_ALREADY_FAILED);
        }

        if (decision.type() == CancelIdempotencyDecisionType.PROCESSING) {
            throw new BusinessException(TransactionErrorCode.CANCEL_ALREADY_PROCESSING);
        }
        if (decision.type() == CancelIdempotencyDecisionType.CONFLICT) {
            throw new BusinessException(TransactionErrorCode.IDEMPOTENCY_CONFLICT);
        }

        /** 2. 검증 + CANCEL 저장 + Processing - REQUIRES_NEW 트랜잭션으로 커밋 */
        CancelExecutionPrepared prepared =
                cancelExecutionStateWriter.prepareCancel(
                        merchantPartyId, transactionId, request.paymentPin());

        /** 3. BANK 취소 호출 - DB 트랜잭션 밖에서 실행 */
        BankOutcome<CancelResponse> outcome =
                callBankWithRetry(
                        () -> bankClient.cancel(prepared.toBankCancelRequest()),
                        CANCEL_BANK_FAIL_CODE_MAP,
                        TransactionErrorCode.CANCEL_FAILED);

        /** 4. 결과 분류 및 상태 반영 */
        PaymentCancelResponse response =
                switch (outcome.type()) {
                    case SUCCESS ->
                            cancelExecutionStateWriter.completeSuccess(
                                    prepared.cancelTransactionUuid(),
                                    outcome.value().txHash(),
                                    String.valueOf(outcome.value().bankTransactionId()),
                                    outcome.value().confirmedAt());
                    case UNKNOWN ->
                            cancelExecutionStateWriter.markUnknown(
                                    prepared.cancelTransactionUuid());
                    case TERMINAL_FAILED -> {
                        cancelExecutionStateWriter.completeFailed(prepared.cancelTransactionUuid());
                        throw new BusinessException(outcome.errorCode());
                    }
                };

        /** 5. 멱등성 snapshot 저장 */
        cancelIdempotencyStore.completeCancel(originalTransactionUuid, response);

        return response;
    }

    private PaymentCancelResponse doRecoverCancel(Long merchantPartyId, Long transactionId) {
        // 1. 정상 조회: SUCCESS/FAILED/PROCESSING을 applyRecoveryResult가 반영한다.
        CancelExecutionPrepared prepared =
                cancelExecutionStateWriter.prepareRecovery(merchantPartyId, transactionId);
        String cancelUuid = prepared.cancelTransactionUuid();

        // Bank 조회로 결과 확정 (404 → FAILED)
        PaymentCancelResponse response = resolveCancelRecovery(cancelUuid);

        // 종단으로 끝났으면 Redis 멱등 record 정리
        if (response.status() == TransactionStatus.SUCCESS) {
            cancelIdempotencyStore.completeCancel(prepared.originalTransactionUuid(), response);
        } else if (response.status() == TransactionStatus.FAILED) {
            cancelIdempotencyStore.failCancel(prepared.originalTransactionUuid());
        } else {
            // 은행이 아직 처리 중 → 시도 횟수만 올림
            cancelExecutionStateWriter.incrementRecoveryAttempt(cancelUuid);
        }

        return response;
    }

    /**
     * bankClient 호출 전 종료된 요청들은 PROCESSING 레코드가 저장되고 고아상태에 빠진다. 이런 경우는 은행쪽에 조회 응답이 404 - NOT FOUND로
     * 반환 된다.
     */
    private PaymentCancelResponse resolveCancelRecovery(String cancelUuid) {
        try {
            // 1. 정상 조회: SUCCESS/FAILED/PROCESSING을 applyRecoveryResult가 반영한다.
            BankTransactionStatusResponse bankStatus = bankClient.getTransactionStatus(cancelUuid);
            return cancelExecutionStateWriter.applyRecoveryResult(cancelUuid, bankStatus);
        } catch (RestClientResponseException e) {
            // 2. 404가 아니면 (5xx 등) 일시적 오류 같은 경우 다시 던져서 다음 sweep에 재시도한다.
            if (!e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw e;
            }
            // 3. 404는 은행 DB 원장에 기록자체가 없다. -> 은행 도달전 사망했다는 의미로 FAILED 확정 (플랫폼의 책임)
            // applyRecoveryResult의 FAILED 처리를 그대로 재사용하기 위해 FAILED status 합성
            BankTransactionStatusResponse asFailed =
                    BankTransactionStatusResponse.failed(cancelUuid);
            return cancelExecutionStateWriter.applyRecoveryResult(cancelUuid, asFailed);
        }
    }

    private String getTransactionUuid(Long transactionId) {
        return transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.PAYMENT_NOT_FOUND))
                .getTransactionUuid();
    }

    private Party getParty(Long partyId) {
        return partyRepository
                .findById(partyId)
                .orElseThrow(() -> new BusinessException(GeneralErrorCode.COMMON_NOT_FOUND));
    }

    private Merchant getMerchant(Long merchantPartyId) {
        return merchantRepository
                .findByParty_Id(merchantPartyId)
                .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
    }

    private Wallet getWallet(Long partyId) {
        return walletRepository
                .findByParty_Id(partyId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
    }

    /** Bank 쓰기 호출 + 일시적 오류 1회 재시도를 한다. bank가 transactionUuid로 멱등 처리를 하므로 재호출은 안전하다. */
    private <T> BankOutcome<T> callBankWithRetry(
            Supplier<T> bankCall,
            Map<String, BaseErrorCode> failCodeMap,
            BaseErrorCode fallbackCode) {
        // 1. 1차 시도
        try {
            return BankOutcome.success(bankCall.get()); // Supplier 로 제네릭하게 호출
        } catch (RuntimeException first) {
            if (!isRetryable(first)) {
                return BankOutcome.failed(
                        resolveErrorCode(
                                first,
                                failCodeMap,
                                fallbackCode)); // 비지니스 로직상 불가능한 것들은 FAILED 처리 (ex: 잔액부족)
            }
            log.warn("Bank 호출 일시적 오류, 1회 재시도. reason={}", first.getMessage());

            // 2. 백오프 대기 중 인터럽트되면 재시도 포기하고 UNKNOWN (스케줄러가 정산)
            if (!sleepBeforeRetry()) {
                log.warn("재시도 대기 중 인터럽트 발생 - UNKNOWN으로 변경");
                return BankOutcome.unknown();
            }
        }

        // 3. 재시도 (같은 client 쓰기 호출)
        try {
            return BankOutcome.success(bankCall.get());
        } catch (RuntimeException retry) {
            if (isRetryable(retry)) {
                return BankOutcome.unknown(); // 여전히 불확실한 것들은 UNKNOWN 처리 후 스케줄러에게 위임
            }
            return BankOutcome.failed(
                    resolveErrorCode(
                            retry, failCodeMap, fallbackCode)); // 재시도 중 종단 실패로 확정 (보상의 보상을 하지않기 위함)
        }
    }

    /**
     * Bank에서 재시도 할만한 비지니스 예외는 TRANSACTION_DUPLICATE_PROCESSING 뿐이다. 이외에는 모두 FAILED로 보면 된다.
     *
     * <p>TRANSACTION_DUPLICATE_PROCESSING 409 일시적 → 재시도 TRANSACTION_ALREADY_FAILED 422 종단
     * TRANSACTION_INSUFFICIENT_BALANCE 400 종단 TRANSACTION_NOT_FOUND 404 종단 EXCHANGE_CONTRACT_FAILED
     * 502 종단인데 5xx ️ BLOCKCHAIN_LEDGER_NOT_FOUND 500 종단인데 5xx ️
     */
    private boolean isRetryable(RuntimeException exception) {
        /** 네트워크 I/O를 하는 도중 생기는 예외 */
        if (exception instanceof ResourceAccessException) {
            return true;
        }

        /** bankClient가 호출하는 예외 */
        if (exception instanceof RestClientResponseException rcre) {
            // 1. bankClient 응답 코드 확인 (5xx같은 코드 거르기)
            Optional<String> code = parseBankErrorCode(rcre);

            // 2. 재시도 할만한 비지니스 예외 확인
            if (code.isPresent()) {
                return RETRYABLE_BANK_CODES.contains(code.get());
            }
            // 3. code를 못읽으면 순수 5xx, 409만 재시도한다.
            return rcre.getStatusCode().is5xxServerError()
                    || rcre.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT);
        }

        return false;
    }

    /** 종단 실패의 정규화 코드 결정. */
    private BaseErrorCode resolveErrorCode(
            RuntimeException ex,
            Map<String, BaseErrorCode> failCodeMap,
            BaseErrorCode fallbackCode) {
        if (ex instanceof BusinessException be) {
            return be.getCode();
        }
        if (ex instanceof RestClientResponseException rcre) {
            String bankCode = parseBankErrorCode(rcre).orElse(null);
            return failCodeMap.getOrDefault(bankCode, fallbackCode);
        }
        return fallbackCode;
    }

    private Optional<String> parseBankErrorCode(RestClientResponseException rcre) {
        try {
            // ObjectMapper를 통해 JSON를 객체로 역직렬화하여, BankErrorBody(code, message)만 추출함.
            BankErrorBody body =
                    BANK_ERROR_MAPPER.readValue(
                            rcre.getResponseBodyAsString(), BankErrorBody.class); //
            // code 추출
            return Optional.ofNullable(body).map(BankErrorBody::code);
        } catch (Exception ignore) {
            return Optional.empty();
        }
    }

    /** 백오프 대기 - 인터럽트 되면 flag 복원 후 false -> 호출부가 UNKNOWN 처리 */
    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(BANK_RETRY_DELAY_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 플래그 복원 (셧다운 로직이 인지)
            return false;
        }
    }
}
