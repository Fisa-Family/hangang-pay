package family.fisa.hangangpay.domain.transaction.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ExchangeExecuteRequest(
        @NotNull String transactionUuid,
        @NotNull Long walletId,
        @NotNull Long accountId,
        @NotNull BigDecimal amount,
        BigDecimal discountAmount,
        BigDecimal discountRate) {}
