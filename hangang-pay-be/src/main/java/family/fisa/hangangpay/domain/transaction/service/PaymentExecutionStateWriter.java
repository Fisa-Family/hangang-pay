package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentExecutionResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.internal.payment.*;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class PaymentExecutionStateWriter {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final PaymentIdempotencyStore paymentIdempotencyStore;
    private final PaymentRateLimiter paymentRateLimiter;
    private final PaymentRequestHashGenerator paymentRequestHashGenerator;

    public PaymentExecutionPreparationResult prepareExecution(
            Long userId, Long partyId, String transactionUuid, String paymentPin) {
        Transaction transaction = getPaymentTransaction(transactionUuid);
        User user = getUser(userId);

        transaction.validateOwner(partyId);

        if (!user.matchesPaymentPin(paymentPin, passwordEncoder)) {
            throw new BusinessException(UserErrorCode.INVALID_PIN_NUMBER);
        }

        String requestHash = paymentRequestHashGenerator.generatePaymentExecuteHash(transaction);

        PaymentIdempotencyDecision decision =
                paymentIdempotencyStore.beginExecution(
                        transactionUuid, requestHash, transaction.getId());

        if (decision.type() == PaymentIdempotencyDecisionType.RETURN_SNAPSHOT) {
            return PaymentExecutionPreparationResult.snapshot(decision.responseSnapshot());
        }

        if (decision.type() == PaymentIdempotencyDecisionType.CONFLICT) {
            throw new BusinessException(TransactionErrorCode.IDEMPOTENCY_CONFLICT);
        }

        if (decision.type() == PaymentIdempotencyDecisionType.PROCESSING) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_ALREADY_PROCESSING);
        }

        transaction.validateExecutableStatus();

        paymentRateLimiter.checkExecutionRateLimit(
                partyId, transaction.getToParty().getId(), transactionUuid);

        paymentRateLimiter.checkBankOutboundRateLimit();

        transaction.markProcessing();

        return PaymentExecutionPreparationResult.prepared(
                PaymentExecutionPrepared.from(transaction, requestHash));
    }

    public PaymentExecutionResponse markUnknown(String transactionUuid) {
        Transaction transaction = getPaymentTransaction(transactionUuid);
        transaction.markUnknown();

        return PaymentExecutionResponse.from(transaction, null, LocalDateTime.now());
    }

    public PaymentExecutionResponse completeSuccess(
            String transactionUuid,
            String txHash,
            String bankTransactionId,
            LocalDateTime confirmedAt) {
        // 1. PROCESSING 상태 거래 조회
        Transaction transaction = getPaymentTransaction(transactionUuid);
        Merchant merchant = getMerchant(transaction.getToParty().getId());

        // 2. txHash, bankTransactionId 기록 후 SUCCESS 전환
        transaction.completeWithBankResponse(txHash, bankTransactionId);

        // 3. 승인번호 생성 — id는 createPaymentIntent 시점에 이미 채번됨
        transaction.assignApprovalNumber(makeApvNumber(transaction.getId()));

        return PaymentExecutionResponse.from(transaction, merchant.getMerchantName(), confirmedAt);
    }



    /** 내부 메소드 */
    private User getUser(Long userId) {
        return userRepository
                .findByIdWithParty(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private Transaction getPaymentTransaction(String transactionUuid) {
        return transactionRepository
                .findByTransactionUuid(transactionUuid)
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.PAYMENT_NOT_FOUND));
    }

    private Merchant getMerchant(Long partyId) {
        return merchantRepository
                .findByParty_Id(partyId)
                .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
    }

    private String makeApvNumber(Long id) {
        return "APV-" + LocalDateTime.now().getYear() + "-" + String.format("%08d", id);
    }
}
