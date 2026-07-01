package family.fisa.hangangpay.domain.transaction.infra.redis;

import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;

/** Redis 충전 멱등성 레코드 */
public record ChargeIdempotencyRecord(
        String transactionUuid, // 거래 식별자
        String requestHash, // 요청 해시 (충돌 감지용 SHA-256)
        TransactionStatus status, // 현재 거래 상태
        Long transactionId, // DB transaction PK
        ChargeExecuteResponse responseSnapshot) { // 멱등 재사용 응답 (완료 전 null)

    /** PROCESSING 상태의 초기 레코드 생성 */
    public static ChargeIdempotencyRecord processing(
            String transactionUuid, String requestHash, Long transactionId) {
        return new ChargeIdempotencyRecord(
                transactionUuid, requestHash, TransactionStatus.PROCESSING, transactionId, null);
    }

    /** snapshot 반영 후 완료 레코드 반환 */
    public ChargeIdempotencyRecord complete(ChargeExecuteResponse responseSnapshot) {
        return new ChargeIdempotencyRecord(
                transactionUuid,
                requestHash,
                responseSnapshot != null ? TransactionStatus.SUCCESS : status,
                transactionId,
                responseSnapshot);
    }

    /** 상태만 교체한 새 레코드 반환 */
    public ChargeIdempotencyRecord withStatus(TransactionStatus status) {
        return new ChargeIdempotencyRecord(
                transactionUuid, requestHash, status, transactionId, responseSnapshot);
    }
}
