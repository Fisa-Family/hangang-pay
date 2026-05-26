package family.fisa.hangangpay.domain.transaction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ExchangeExecuteRequest(
        @NotBlank String transactionUuid,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String paymentPin) {}
