package family.fisa.hangangpay.domain.transaction.dto.user.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentCancelRequest(@NotBlank(message = "결제 비밀번호를 입력해주세요.") String paymentPin) {}
