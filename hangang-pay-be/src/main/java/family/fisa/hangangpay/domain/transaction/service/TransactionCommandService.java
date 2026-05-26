package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.BankTransactionStatusResponse;
import family.fisa.hangangpay.client.bank.dto.PaymentResponse;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentExecutionResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.*;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.code.error.WalletErrorCode;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TransactionCommandService {
    private static final long PAYMENT_INTENT_TTL_MINUTES = 10L;

    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final PartyRepository partyRepository;
    private final BankClient bankClient;
    private final PaymentIdempotencyStore paymentIdempotencyStore;
    private final PaymentLockManager paymentLockManager;
    private final PaymentRateLimiter paymentRateLimiter;
    private final PaymentExecutionStateWriter paymentExecutionStateWriter;

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
                        executePaymentWithLock(
                                userId, partyId, transactionUuid, request)); // 콜백으로 락 걸고 이어서 수행
    }

    public PaymentExecutionResponse recoverPayment(Long partyId, String transactionUuid) {
        return paymentLockManager.withTransactionLock(
                transactionUuid, () -> recoverPaymentWithLock(partyId, transactionUuid));
    }

    /** 내부 메소드 */
    private PaymentExecutionResponse executePaymentWithLock(
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
        PaymentResponse bankResponse;
        try {
            bankResponse = bankClient.payment(prepared.toBankPaymentRequest());
        } catch (ResourceAccessException ex) {
            /** 3-1. 연결 실패된 기존 트랜잭션 수정 - UNKNOWN */
            PaymentExecutionResponse response =
                    paymentExecutionStateWriter.markUnknown(transactionUuid);

            paymentIdempotencyStore.markExecutionStatus(transactionUuid, TransactionStatus.UNKNOWN);
            return response;
        }

        /** 3-2. 거래 완료된 기존 트랜잭션 수정 - SUCCESS */
        PaymentExecutionResponse response =
                paymentExecutionStateWriter.completeSuccess(
                        transactionUuid,
                        bankResponse.txHash(),
                        String.valueOf(bankResponse.blockNumber()),
                        bankResponse.confirmedAt());

        /** 4. Redis용 idempotency snapshot 저장 */
        paymentIdempotencyStore.completeExecution(transactionUuid, response);

        return response;
    }

    private PaymentExecutionResponse recoverPaymentWithLock(Long partyId, String transactionUuid) {
        Transaction transaction = getTransaction(transactionUuid);

        transaction.validateOwner(partyId);
        transaction.validateRecoverableStatus();

        /** 요청량 확인 */
        paymentRateLimiter.checkRecoveryRateLimit(partyId, transactionUuid);
        paymentRateLimiter.checkBankOutboundRateLimit();

        /** Bank 호출 */
        BankTransactionStatusResponse bankStatus = bankClient.getTransactionStatus(transactionUuid);

        /** 은행 SUCCESS -> 플랫폼 SUCCESS */
        if (bankStatus.status() == TransactionStatus.SUCCESS) {
            validateBankSuccessRecoveryResult(bankStatus);
            transaction.recoverSuccess(
                    bankStatus.txHash(), String.valueOf(bankStatus.blockNumber()));
        }

        /** 은행 FAILED -> 플랫폼 FAILED */
        if (bankStatus.status() == TransactionStatus.FAILED) {
            transaction.recoverFailed();
        }

        /** Bank 조회와 상태 반영 후 merchant 조회 -> 응답 조립용 */
        Merchant merchant = getMerchant(transaction.getToParty().getId());

        return PaymentExecutionResponse.from(
                transaction, merchant.getMerchantName(), bankStatus.confirmedAt());
    }

    private void validateBankSuccessRecoveryResult(BankTransactionStatusResponse bankStatus) {
        if (bankStatus.txHash() == null || bankStatus.blockNumber() == null) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);
        }
    }

    private Transaction getTransaction(String transactionUuid) {
        return transactionRepository
                .findByTransactionUuid(transactionUuid)
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.PAYMENT_NOT_FOUND));
    }

    private Party getParty(Long partyId) {
        return partyRepository
                .findById(partyId)
                .orElseThrow(() -> new BusinessException(GeneralErrorCode.COMMON_NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository
                .findByIdWithParty(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
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
}
