package family.fisa.hangangpaybank.domain.transaction.dto.request;

import java.math.BigDecimal;

public record PaymentRequest(
        String transactionUuid,
        String fromWalletAddress,
        String toWalletAddress,
        BigDecimal amount) {}
