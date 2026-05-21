package family.fisa.hangangpay.domain.transaction.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/** 충전 금액 및 할인 계산 응답 DTO */
@Getter
@Builder
public class ChargeCalculateResponse {

    /** 충전 요청 금액 */
    private BigDecimal chargeAmount;

    /** 할인율 */
    private BigDecimal discountRate;

    /** 할인 금액 */
    private BigDecimal discountAmount;

    /** 실 결제 금액, 충전 금액에서 할인 금액을 뺀 값 */
    private BigDecimal actualPayAmount;
}
