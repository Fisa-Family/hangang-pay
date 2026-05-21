package family.fisa.hangangpay.domain.transaction.dto.request;

import jakarta.validation.constraints.NotNull;

public record PaymentCancelRequest(
        @NotNull String transactionUuid, @NotNull String originalTransactionUuid) {}
