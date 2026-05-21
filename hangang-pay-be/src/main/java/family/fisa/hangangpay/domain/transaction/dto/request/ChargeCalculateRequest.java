package family.fisa.hangangpay.domain.transaction.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 충전 금액 계산 요청 DTO */
@Getter
@NoArgsConstructor
public class ChargeCalculateRequest {

    /** 충전 요청 금액, 만원 단위 */
    @NotNull @Positive private BigDecimal chargeAmount;
}
