package family.fisa.hangangpay.domain.transfer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** 충전 실행 응답 DTO */
@Getter
@Builder
public class ChargeReceiptResponse {

    /** 파티 식별자 */
    private Long partyId;

    /** 충전 내역 식별자 (fund_transfer.id) */
    private Long chargeId;

    /** 충전 요청 금액 (액면가) */
    private BigDecimal amount;

    /** 실 결제 금액 (10% 할인 적용) */
    private BigDecimal finalAmount;

    /** 충전 요청 시각 */
    private LocalDateTime chargedAt;
}
