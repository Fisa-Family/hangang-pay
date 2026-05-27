package family.fisa.hangangpay.domain.transaction.internal;

import family.fisa.hangangpay.domain.transaction.dto.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;

/** 충전 멱등성 판단 및 상태 관리 포트 */
public interface ChargeIdempotencyStore {

    /** 실행 시작 및 멱등성 판단 결과 반환 */
    ChargeIdempotencyDecision beginExecution(
            String transactionUuid, String requestHash, Long transactionId);

    /** 완료 응답 snapshot 저장 */
    void completeExecution(String transactionUuid, ChargeExecuteResponse responseSnapshot);

    /** 실행 상태 갱신 */
    void markExecutionStatus(String transactionUuid, TransactionStatus status);
}
