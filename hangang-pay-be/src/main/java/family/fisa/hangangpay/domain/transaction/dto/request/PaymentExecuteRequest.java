package family.fisa.hangangpay.domain.transaction.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentExecuteRequest(
        @NotNull String transactionUuid,
        @NotNull Long fromWalletId,
        @NotNull Long toWalletId,
        @NotNull BigDecimal amount,
        String itemName) {}
