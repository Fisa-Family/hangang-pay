package family.fisa.hangangpay.domain.user.dto;

public enum UserHistoryType {
    PAYMENT,
    CANCEL, // 쿼리 파라미터로 직접 사용 X, ALL/PAYMENT 응답 원소 표기용
    CHARGE,
    EXCHANGE,
    ALL // 전체 조회
}
