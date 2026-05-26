package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.ExchangeResponse;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.ReconcileResult;
import family.fisa.hangangpay.domain.transaction.dto.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** EXCHANGE 명령 오케스트레이터 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeCommandService {

    /** 환전 자격 : 마지막 충전 직후 잔액의 60% 이상을 결제로 사용해야 함 */
    private static final BigDecimal USAGE_THRESHOLD_RATE = new BigDecimal("0.60");

    /** PENDING 거래 임계 시간 - 초과 시 orphan으로 간주하고 reconcile 시도 */
    private static final int ORPHAN_THRESHOLD_MINUTES = 5;

    private final TransactionRepository transactionRepository;
    private final ExchangeStateWriter stateWriter;
    private final BankClient bankClient;
    private final ExchangeReconcileService exchangeReconcileService;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;

    /** 사용자 환전 실행 */
    public ExchangeExecuteResponse executeUserExchange(
            Long partyId, ExchangeExecuteRequest request) {
        log.info(
                "환전 실행 시작. partyId={}, transactionUuid={}, amount={}",
                partyId,
                request.transactionUuid(),
                request.amount());

        // 1. 핀번호 검증
        verifyUserPaymentPin(partyId, request.paymentPin());

        // 2. 멱등 체크: 같은 transactionUuid가 이미 있으면 상태별 처리
        Optional<ExchangeExecuteResponse> idempotentHit =
                checkIdempotency(request.transactionUuid());

        if (idempotentHit.isPresent()) {
            log.info("멱등 hit. partyId={}, transactionUuid={}", partyId, request.transactionUuid());
            return idempotentHit.get();
        }

        // 3. 환전 자격 검증 (마지막 충전 직후 잔액의 60% 이상 사용)
        verifyEligibility(partyId);

        // 4. 슬롯 선점: wallet 락 + inflight 체크 + PENDING 저장(별도 Tx)
        Long transactionId = stateWriter.claimExchange(partyId, request);

        return runBankExchange(partyId, request, transactionId);
    }

    /** 가맹점 환전 실행 */
    public ExchangeExecuteResponse executeMerchantExchange(
            Long partyId, ExchangeExecuteRequest request) {
        log.info(
                "환전 실행 시작(merchant). partyId={}, transactionUuid={}, amount={}",
                partyId,
                request.transactionUuid(),
                request.amount());

        // 1. 핀번호 검증
        verifyMerchantPaymentPin(partyId, request.paymentPin());

        // 2. 멱등 체크: 같은 transactionUuid가 이미 있으면 상태별 처리
        Optional<ExchangeExecuteResponse> idempotentHit =
                checkIdempotency(request.transactionUuid());
        if (idempotentHit.isPresent()) {
            log.info("멱등 hit. partyId={}, transactionUuid={}", partyId, request.transactionUuid());
            return idempotentHit.get();
        }

        Long transactionId = stateWriter.claimSettlementExchange(partyId, request);
        return runBankExchange(partyId, request, transactionId);
    }

    /** claim 이후 bank 호출 로직 */
    private ExchangeExecuteResponse runBankExchange(
            Long partyId, ExchangeExecuteRequest request, Long transactionId) {
        // 1. 외부 호출: 락/Tx 밖에서 bank 호출
        ExchangeResponse bankResponse;
        try {
            bankResponse =
                    bankClient.exchange(stateWriter.buildBankRequest(transactionId, request));
        } catch (RuntimeException ex) {

            log.error(
                    "환전 실행 실패. partyId={}, transactionUuid={}",
                    partyId,
                    request.transactionUuid(),
                    ex);

            stateWriter.failExchange(transactionId);
            throw ex;
        }

        // 2. 완료 마킹 (별도 Tx) + 응답 빌드
        ExchangeExecuteResponse response =
                stateWriter.completeExchange(
                        transactionId,
                        bankResponse.txHash(),
                        String.valueOf(bankResponse.bankTransactionId()));

        log.info(
                "환전 실행 완료. partyId={}, transactionId={}, txHash={}",
                partyId,
                transactionId,
                bankResponse.txHash());

        return response;
    }

    /** 멱등 체크 */
    private Optional<ExchangeExecuteResponse> checkIdempotency(String transactionUuid) {
        return transactionRepository
                .findByTransactionUuid(transactionUuid)
                .map(
                        tx ->
                                switch (tx.getStatus()) {
                                    case SUCCESS ->
                                            ExchangeExecuteResponse.from(
                                                    tx); // SUCCESS -> 기존 결과 반환 (멱등 hit)
                                    case PENDING ->
                                            handlePending(
                                                    tx); // PENDING -> 진행 중 에러 또는 reconcile 트리거
                                    case FAILED ->
                                            throw new BusinessException(
                                                    TransactionErrorCode
                                                            .EXCHANGE_ALREADY_FAILED); // FAILED  ->
                                        // 이미 실패 에러
                                });
    }

    /** 사용자 결제 PIN 검증 */
    private void verifyUserPaymentPin(Long partyId, String paymentPin) {
        User user =
                userRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (!user.matchesPaymentPin(paymentPin, passwordEncoder)) {
            log.warn("환전 PIN 불일치(user). partyId={}", partyId);
            throw new BusinessException(TransactionErrorCode.INVALID_PAYMENT_PIN);
        }
    }

    /** 가맹점 결제 PIN 검증 */
    private void verifyMerchantPaymentPin(Long partyId, String paymentPin) {
        Merchant merchant =
                merchantRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(
                                () -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

        if (!merchant.matchesPaymentPin(paymentPin, passwordEncoder)) {
            log.warn("환전 PIN 불일치(merchant). partyId={}", partyId);
            throw new BusinessException(TransactionErrorCode.INVALID_PAYMENT_PIN);
        }
    }

    /** PENDING 분기 처리 - 임계 시간을 초과하면 인라인 reconcile, 아니면 in-flight로 거절 */
    private ExchangeExecuteResponse handlePending(Transaction tx) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(ORPHAN_THRESHOLD_MINUTES);

        // 임계 시간 이내(만들어진지 5분이 안됨) - 정상 in-flight
        if (tx.getCreatedAt().isAfter(threshold)) {
            throw new BusinessException(TransactionErrorCode.EXCHANGE_IN_PROGRESS);
        }

        // 임계 시간 초과 - orphan 의심, 바로 reconcile
        log.warn(
                "PENDING 환전이 임계 시간 초과. reconcile 시도. transactionId={}, transactionUuid={}, createdAt={}",
                tx.getId(),
                tx.getTransactionUuid(),
                tx.getCreatedAt());

        ReconcileResult result = exchangeReconcileService.reconcile(tx.getId());

        // RECONCILED_SUCCESS - 재조회해서 SUCCESS로 마킹된 tx로 응답 생성(처리해서 PENDING -> SUCCESS로 변환완료 했기 때문)
        if (result == ReconcileResult.RECONCILED_SUCCESS) {
            Transaction updated =
                    transactionRepository
                            .findByTransactionUuid(tx.getTransactionUuid())
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    TransactionErrorCode.EXCHANGE_NOT_FOUND));
            return ExchangeExecuteResponse.from(updated);
        }

        // RECONCILED_FAILED - bank에 거래 없음 확인됨(PENDING -> FAILED 마킹됨)
        if (result == ReconcileResult.RECONCILED_FAILED) {
            throw new BusinessException(TransactionErrorCode.EXCHANGE_ALREADY_FAILED);
        }

        // SKIPPED, RECONCILE_ERROR - reconcile 결론 못 냄, 다시 in-flight로 응답 (reconcile 했을 때 PENDING이
        // 아니었음 동시에 끝난 경우)
        throw new BusinessException(TransactionErrorCode.EXCHANGE_IN_PROGRESS);
    }

    /** 환전 자격 검증 환전 자격 = (마지막 충전 직후 잔액) × 60% <= (마지막 충전 이후 SUCCESS PAYMENT 합계) */
    private void verifyEligibility(Long partyId) {
        // 1. 가장 마지막 충전 성공 거래 가져오기
        Transaction latestCharge =
                transactionRepository
                        .findLatestSuccessCharge(partyId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.EXCHANGE_NOT_ELIGIBLE));

        LocalDateTime chargeAt = latestCharge.getCreatedAt();

        // 2. 마지막 충전 직전 시점에 잔액 계산
        BigDecimal chargedBefore =
                transactionRepository.sumSuccessByTypeBefore(
                        partyId, TransactionType.CHARGE, chargeAt);
        BigDecimal paidBefore =
                transactionRepository.sumSuccessByTypeBefore(
                        partyId, TransactionType.PAYMENT, chargeAt);
        BigDecimal exchangedBefore =
                transactionRepository.sumSuccessByTypeBefore(
                        partyId, TransactionType.EXCHANGE, chargeAt);

        // 3. 충전 직전 잔액 = (그동안 충전한 총액) - (결제로 나간 총액) - (환전으로 나간 총액)
        BigDecimal balanceBefore = chargedBefore.subtract(paidBefore).subtract(exchangedBefore);

        // 4. 마지막 충전이 반영된 직후의 잔액
        BigDecimal balanceAfter = balanceBefore.add(latestCharge.getAmount());

        // 5. 환전 자격 값 = balanceAfter * 60%
        BigDecimal threshold =
                balanceAfter.multiply(USAGE_THRESHOLD_RATE).setScale(0, RoundingMode.UP);

        // 6. 마지막 충전 시점 이후 실제 사용액(SUCCESS PAYMENT) 합산.
        BigDecimal usedSinceCharge =
                transactionRepository.sumSuccessByTypeSince(
                        partyId, TransactionType.PAYMENT, chargeAt);

        // 7. 사용액이 임계값에 못 미치면 환전 거절
        if (usedSinceCharge.compareTo(threshold) < 0) {
            log.warn(
                    "환전 자격 미달. partyId={}, balanceAfter={}, threshold={}, used={}",
                    partyId,
                    balanceAfter,
                    threshold,
                    usedSinceCharge);
            throw new BusinessException(TransactionErrorCode.EXCHANGE_NOT_ELIGIBLE);
        }
    }
}
