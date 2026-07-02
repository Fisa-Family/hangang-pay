package family.fisa.hangangpay.domain.transaction.service.charge;

import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ChargeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeIntentResponse;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeExecutionPreparationResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** CHARGE(충전) 상태 쓰기 전담. 각 메서드는 REQUIRES_NEW로 독립 트랜잭션을 커밋한다. */
public interface ChargeStateWriter {

    /** intent 생성 = PENDING (금액·출금 계좌·할인액 바인딩). transactionUuid는 서버가 발급한다. */
    ChargeIntentResponse createIntent(
            Long partyId, ChargeIntentCreateRequest request, LocalDateTime expiresAt);

    /** 충전 거래 실행 준비: 검증, 멱등성 판단, PROCESSING 전환 */
    ChargeExecutionPreparationResult prepareProcessing(
            Long partyId, String transactionUuid, String paymentPin);

    /** 충전 성공 처리 */
    ChargeExecuteResponse completeSuccess(
            String transactionUuid,
            String txHash,
            String bankTransactionId,
            LocalDateTime confirmedAt,
            BigDecimal walletBalance);

    /** 충전 상태 불명 처리 */
    ChargeExecuteResponse markUnknown(String transactionUuid);

    /** 충전 실패 처리 */
    ChargeExecuteResponse markFailed(String transactionUuid);

    /** TTL 지난 PENDING intent를 EXPIRED 처리 */
    void markExpired(String transactionUuid);

    /** reconcile: 은행 재조회 결과를 반영하고 응답을 만든다(SUCCESS/FAILED만 상태 전환, PROCESSING은 무변경). */
    ChargeExecuteResponse applyReconcileResult(
            String transactionUuid, BankTransactionStatusResponse bankStatus);

    /** reconcile 시도 횟수 증가 (PROCESSING 유지) */
    void incrementReconcileAttempt(String transactionUuid);
}
