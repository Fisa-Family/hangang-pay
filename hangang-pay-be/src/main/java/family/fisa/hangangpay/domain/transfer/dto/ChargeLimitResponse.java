package family.fisa.hangangpay.domain.transfer.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/** 충전 한도 조회 응답 DTO */
@Getter
@Builder
public class ChargeLimitResponse {

    /** 월 최대 충전 한도 */
    private BigDecimal monthlyLimit;

    /** 이번 달 누적 충전 금액 */
    private BigDecimal usedAmount;

    /** 잔여 충전 가능 금액 */
    private BigDecimal remainLimit;

    /** 한도 초기화 날짜, 다음 달 1일 */
    private String resetDate;
}
