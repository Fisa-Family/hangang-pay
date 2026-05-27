package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.ChargeExecutionPreparationResult;
import family.fisa.hangangpay.domain.transaction.internal.ChargeExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.internal.ChargeIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.ChargeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.ChargeRequestHashGenerator;
import family.fisa.hangangpay.domain.transaction.internal.PaymentIdempotencyDecisionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class ChargeExecutionWriter {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChargeIdempotencyStore chargeIdempotencyStore;
    private final ChargeRequestHashGenerator chargeRequestHashGenerator;

    /** 충전 거래 실행 준비: 검증, 멱등성 판단, PROCESSING 전환 */
    public ChargeExecutionPreparationResult prepareProcessing(
            Long partyId,
            Long institutionId,
            Long accountId,
            BigDecimal amount,
            String transactionUuid,
            String paymentPin) {

        // PENDING CHARGE 조회
        Transaction transaction =
                transactionRepository
                        .findByTransactionUuid(transactionUuid)
                        .filter(t -> t.getTransactionType() == TransactionType.CHARGE)
                        .orElseThrow(
                                () -> new BusinessException(TransactionErrorCode.CHARGE_NOT_FOUND));

        // 소유권 검증
        transaction.validateOwner(partyId);

        // 계좌 소유권 검증
        Account account =
                accountRepository
                        .findByIdAndParty_Id(accountId, partyId)
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // PIN 검증
        User user =
                userRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        if (!user.matchesPaymentPin(paymentPin, passwordEncoder)) {
            throw new BusinessException(TransactionErrorCode.INVALID_PAYMENT_PIN);
        }

        // 요청 중복 여부 확인
        String requestHash =
                chargeRequestHashGenerator.generate(transactionUuid, partyId, accountId, amount);

        ChargeIdempotencyDecision decision =
                chargeIdempotencyStore.beginExecution(
                        transactionUuid, requestHash, transaction.getId());

        if (decision.type() == PaymentIdempotencyDecisionType.RETURN_SNAPSHOT) {
            return ChargeExecutionPreparationResult.snapshot(decision.responseSnapshot());
        }
        if (decision.type() == PaymentIdempotencyDecisionType.CONFLICT) {
            throw new BusinessException(TransactionErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (decision.type() == PaymentIdempotencyDecisionType.PROCESSING) {
            throw new BusinessException(TransactionErrorCode.CHARGE_ALREADY_PROCESSING);
        }

        // 실행 가능 상태 검증 (PENDING)
        transaction.validateExecutableStatus();

        // 금액 계산
        BigDecimal discountRate = transaction.getDiscountRate();
        BigDecimal discountAmount = amount.multiply(discountRate).setScale(0, RoundingMode.DOWN);
        BigDecimal finalAmount = amount.subtract(discountAmount);

        // 충전 거래 실행 준비
        transaction.prepareChargeExecution(account, amount, discountAmount);

        log.info("충전 실행 준비 완료. transactionUuid={}, partyId={}", transactionUuid, partyId);

        return ChargeExecutionPreparationResult.prepared(
                new ChargeExecutionPrepared(
                        transactionUuid,
                        requestHash,
                        institutionId,
                        account.getAccountNumber(),
                        transaction.getToWallet().getAddress(),
                        finalAmount));
    }

    /** 충전 성공 처리 */
    public ChargeExecuteResponse completeSuccess(
            String transactionUuid,
            String txHash,
            String bankTransactionId,
            LocalDateTime confirmedAt) {
        Transaction transaction = getChargeTransaction(transactionUuid);
        transaction.completeWithBankResponse(txHash, bankTransactionId);
        log.info("충전 성공. transactionUuid={}, txHash={}", transactionUuid, txHash);
        return ChargeExecuteResponse.from(transaction, confirmedAt);
    }

    /** 충전 상태 불명 처리 */
    public ChargeExecuteResponse markUnknown(String transactionUuid) {
        Transaction transaction = getChargeTransaction(transactionUuid);
        transaction.markUnknown();
        log.warn("충전 상태 불명. transactionUuid={}", transactionUuid);
        return ChargeExecuteResponse.from(transaction, LocalDateTime.now());
    }

    /** 충전 실패 처리 */
    public ChargeExecuteResponse markFailed(String transactionUuid) {
        Transaction transaction = getChargeTransaction(transactionUuid);
        transaction.markFailed();
        log.warn("충전 실패. transactionUuid={}", transactionUuid);
        return ChargeExecuteResponse.from(transaction, LocalDateTime.now());
    }

    /* 충전 거래 조회 */
    private Transaction getChargeTransaction(String transactionUuid) {
        return transactionRepository
                .findByTransactionUuid(transactionUuid)
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.CHARGE_NOT_FOUND));
    }
}
