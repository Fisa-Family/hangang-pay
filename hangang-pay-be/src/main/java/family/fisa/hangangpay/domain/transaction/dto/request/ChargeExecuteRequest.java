package family.fisa.hangangpay.domain.transaction.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ChargeExecuteRequest(
        @NotNull String transactionUuid,
        @NotNull Long accountId,
        @NotNull Long walletId,
        @NotNull BigDecimal amount,
        BigDecimal discountAmount,
        BigDecimal discountRate) {}
