package family.fisa.hangangpay.domain.transaction.infra.redis.cancel;

import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.infra.redis.IdempotencyRecord;

public record CancelIdempotencyRecord(
        String originalPaymentUuid,
        String requestHash,
        TransactionStatus status,
        PaymentCancelResponse responseSnapshot)
        implements IdempotencyRecord<PaymentCancelResponse> {

    public static CancelIdempotencyRecord processing(
            String originalPaymentUuid, String requestHash) {
        return new CancelIdempotencyRecord(
                originalPaymentUuid, requestHash, TransactionStatus.PROCESSING, null);
    }

    public CancelIdempotencyRecord complete(PaymentCancelResponse responseSnapshot) {
        return new CancelIdempotencyRecord(
                originalPaymentUuid, requestHash, responseSnapshot.status(), responseSnapshot);
    }

    public CancelIdempotencyRecord withStatus(TransactionStatus status) {
        return new CancelIdempotencyRecord(
                originalPaymentUuid, requestHash, status, responseSnapshot);
    }

    public CancelIdempotencyRecord fail() {
        return new CancelIdempotencyRecord(
                originalPaymentUuid, requestHash, TransactionStatus.FAILED, null);
    }
}
