package family.fisa.hangangpay.domain.transaction.internal.exchange;

import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyKey;

public interface ExchangeIdempotencyStore {

    /** transactionUuid를 원자적으로 선점하고 진행 가능 여부를 판정 */
    IdempotencyDecision<ExchangeExecuteResponse> beginExecution(IdempotencyKey idempotencyKey);

    /** Bank 환전 성공 후 최종 응답 snapshot을 저장. */
    void completeExecution(String transactionUuid, ExchangeExecuteResponse responseSnapshot);

    /** Bank 환전 실패 시 record를 FAILED로 마킹 */
    void failExecution(String transactionUuid);
}
