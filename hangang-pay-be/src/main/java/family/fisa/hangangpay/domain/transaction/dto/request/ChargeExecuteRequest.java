package family.fisa.hangangpay.domain.transaction.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 충전 실행 요청 DTO */
public record ChargeExecuteRequest(@NotBlank String paymentPin) {} // 결제 비밀번호
