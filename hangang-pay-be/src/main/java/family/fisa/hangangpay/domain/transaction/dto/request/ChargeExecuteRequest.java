package family.fisa.hangangpay.domain.transaction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** 충전 실행 요청 DTO */
public record ChargeExecuteRequest(
        @NotBlank String transactionUuid, // PENDING 충전 거래 식별자 (멱등키)
        @NotNull Long institutionId, // 출금 계좌 금융기관 ID
        @NotNull Long accountId, // 출금 계좌 ID
        @NotNull @Positive BigDecimal amount, // 충전 요청 금액
        @NotBlank String paymentPin) {} // 결제 비밀번호
