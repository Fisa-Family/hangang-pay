package family.fisa.hangangpay.domain.transaction.internal;

/** 멱등 게이트가 transactionUuid 하나에 대해 내릴 수 있는 판정 종류 (charge/payment/exchange/cancel 공용) */
public enum IdempotencyDecisionType {
    NEW_REQUEST, // 신규 요청
    RETURN_SNAPSHOT, // 이미 성공으로 끝난 요청 — 저장된 응답 재사용
    ALREADY_FAILED, // 이미 실패로 끝난 요청
    PROCESSING, // 아직 결과가 확정되지 않은(진행 중)
    CONFLICT // 같은 transactionUuid이지만 requestHash가 다름
}
