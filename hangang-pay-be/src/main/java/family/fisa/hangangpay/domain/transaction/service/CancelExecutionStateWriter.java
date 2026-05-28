package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class CancelExecutionStateWriter {

    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 취소 사전 처리 - 검증 + CANCEL 저장 + 승인번호 + Processing 전환 이 메서드가 커밋되면, Bank 호출 전까지 CANCEL 레코드가 DB에
     * 남아있다. 네트워크 오류 시 스케줄러가 이를 복구하는 대상으로 인식할 수 있음.
     */
    public CancelExecutionPrepared prepareCancel(
            Long merchantPartyId, Long transactionId, String paymentPin) {
        // 1. 원본 PAYMENT 조회 (fromParty, fromWallet, toWallet fetch join 포함)
        Transaction original = getPaymentTransaction(transactionId);

        // 2. 가맹점 소유권 검증 - 원본 PAYMENT의 toParty가 현재 가맹점인지 확인
        original.validateMerchantIsReceiver(merchantPartyId);

        // 3. 취소 가능 상태 검증 - PAYMENT + SUCCESS 조합만 취소 대상
        original.validateCancellable();

        // 4. 가맹점 PIN 검증
        Merchant merchant = getMerchant(merchantPartyId);

        if (!merchant.matchesPaymentPin(paymentPin, passwordEncoder)) {
            throw new BusinessException(TransactionErrorCode.INVALID_PAYMENT_PIN);
        }

        // 5. SUCCESS CANCEL 중복 차단 - FAILED는 재시도 허용
        if (transactionRepository.existsSuccessCancelFor(original.getTransactionUuid())) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_ALREADY_CANCELLED);
        }

        // 6.CANCEL 거래 생성 - 방향 (가맹점 -> 소비자)
        String cancelUuid = UUID.randomUUID().toString();
        Transaction cancelTx = original.createCancel(cancelUuid);

        // 7. 저장 후 DB 채번된 id 확보
        Transaction saved = transactionRepository.save(cancelTx);

        // 8. Processing 전환 - Bank 호출 중임을 표시
        saved.markProcessing();

        log.info(
                "결제 취소 준비 완료. cancelUuid={}, originalUuid={}",
                cancelUuid,
                original.getTransactionUuid());

        // 9. DB 트랜잭션 커밋 후 Bank 호출에 쓸 불변 스냅샷 반환
        return CancelExecutionPrepared.from(original, saved);
    }

    /**
     * bankClient를 이용해서 은행 API를 호출 할 때, 네트워크 / 인프라 문제로 인해 서버가 끊킬 경우, 해당 Transaction.status를
     * Unknown으로 바꾼다.
     */
    public PaymentCancelResponse markUnknown(String cancelTransactionUuid) {
        // 1. CANCEL 거래 재조회 - prepareCancel는 REQUIRES_NEW로, 이미 커밋되서 해당 엔티티는 detached임.
        // 새 트랜잭션으로 가져와서 markUnknown로 반영한다.
        Transaction cancelTx = getTransactionByUuid(cancelTransactionUuid);

        // 2. Unknown으로 전환 - 은행에서의 처리가 확정되어있지 않으니, FAILED로 확정 X - 나중에 복구API + 스케줄러로 재조회
        cancelTx.markUnknown();

        // 3. WARN 수준의 로그 - 관리자가 인지해야된다.
        log.warn("결제 취소 - UNKNOWN 저장. cancelUuid={}", cancelTransactionUuid);

        // 4. confirmedAt=null - 은행 확정 시각이 없음
        return PaymentCancelResponse.from(cancelTx, null);
    }

    /** Bank cancel 성공 후 CANCEL 거래를 SUCCESS로 확정한다. */
    public PaymentCancelResponse completeSuccess(
            String cancelTransactionUuid,
            String txHash,
            String bankTransactionId,
            LocalDateTime confirmedAt) {
        // 1. CANCEL 거래 조회
        Transaction cancelTx = getTransactionByUuid(cancelTransactionUuid);

        // 2. txHash, bankTransactionId 기록 후 SUCCESS 전환
        cancelTx.completeWithBankResponse(txHash, bankTransactionId);

        // 3. 승인번호 생성 — id는 prepareCancel 시점에 이미 채번됨
        cancelTx.assignApprovalNumber(makeApvNumber(cancelTx.getId()));

        log.info("결제 취소 완료. cancelUuid={}, txHash={}", cancelTransactionUuid, txHash);

        // 4. 응답 조립
        return PaymentCancelResponse.from(cancelTx, confirmedAt);
    }

    /** 내부 메소드 */
    private @NonNull Transaction getPaymentTransaction(Long transactionId) {
        return transactionRepository
                .findDetailByIdAndTypes(transactionId, List.of(TransactionType.PAYMENT))
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.PAYMENT_NOT_FOUND));
    }

    private Transaction getTransactionByUuid(String cancelTransactionUuid) {
        return transactionRepository
                .findByTransactionUuid(cancelTransactionUuid)
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.PAYMENT_NOT_FOUND));
    }

    private Merchant getMerchant(Long merchantPartyId) {
        return merchantRepository
                .findByParty_Id(merchantPartyId)
                .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
    }

    private String makeApvNumber(Long id) {
        return "APV-" + LocalDateTime.now().getYear() + "-" + String.format("%08d", id);
    }
}
