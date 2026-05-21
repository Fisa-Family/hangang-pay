package family.fisa.hangangpay.domain.transaction.dto.response;

import java.math.BigDecimal;

/** 충전 한도 조회 응답 DTO */
public record ChargeLimitResponse(
        /** 월 최대 충전 한도 */
        BigDecimal monthlyLimit,
        /** 이번 달 누적 충전 금액 */
        BigDecimal usedAmount,
        /** 잔여 충전 가능 금액 */
        BigDecimal remainLimit,
        /** 한도 초기화 날짜, 다음 달 1일 */
        String resetDate) {

    public static ChargeLimitResponse of(
            BigDecimal monthlyLimit,
            BigDecimal usedAmount,
            BigDecimal remainLimit,
            String resetDate) {
        return new ChargeLimitResponse(monthlyLimit, usedAmount, remainLimit, resetDate);
    }
}
