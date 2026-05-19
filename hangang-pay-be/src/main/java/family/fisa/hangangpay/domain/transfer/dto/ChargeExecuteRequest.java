package family.fisa.hangangpay.domain.transfer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 충전 실행 요청 DTO */
@Getter
@NoArgsConstructor
public class ChargeExecuteRequest {

    /** 출금 계좌 식별자 */
    @NotNull
    private Long accountId;

    /** 충전 금액 (만원 단위) */
    @NotNull
    @Min(10000)
    private BigDecimal amount;

    /** 결제 PIN */
    @NotBlank
    private String paymentPin;
}
