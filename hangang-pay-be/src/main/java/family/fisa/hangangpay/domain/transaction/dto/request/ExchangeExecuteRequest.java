package family.fisa.hangangpay.domain.transaction.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ExchangeExecuteRequest(@NotBlank String paymentPin) {}
