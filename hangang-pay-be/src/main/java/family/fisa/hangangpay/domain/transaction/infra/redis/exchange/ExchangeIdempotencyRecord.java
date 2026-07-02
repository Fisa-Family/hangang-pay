package family.fisa.hangangpay.domain.transaction.infra.redis.exchange;

import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.infra.redis.IdempotencyRecord;

public record ExchangeIdempotencyRecord(
        String transactionUuid,
        String requestHash,
        TransactionStatus status,
        ExchangeExecuteResponse responseSnapshot)
        implements IdempotencyRecord<ExchangeExecuteResponse> {

    /** 첫 요청 선점 시점. 아직 Bank 결과를 모르므로 UNKNOWN, snapshot 없음. */
    public static ExchangeIdempotencyRecord inFlight(String transactionUuid, String requestHash) {

        return new ExchangeIdempotencyRecord(
                transactionUuid, requestHash, TransactionStatus.UNKNOWN, null);
    }

    /** 성공 완료. snapshot의 status(SUCCESS)를 그대로 반영한다. */
    public ExchangeIdempotencyRecord complete(ExchangeExecuteResponse responseSnapshot) {

        return new ExchangeIdempotencyRecord(
                transactionUuid, requestHash, responseSnapshot.status(), responseSnapshot);
    }

    /** 실패 마킹. snapshot은 두지 않는다. */
    public ExchangeIdempotencyRecord fail() {
        return new ExchangeIdempotencyRecord(
                transactionUuid, requestHash, TransactionStatus.FAILED, null);
    }
}
