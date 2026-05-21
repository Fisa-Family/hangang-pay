package family.fisa.hangangpaybank.domain.transaction.dto.request;

import java.math.BigDecimal;

public record CancelRequest(
        String transactionUuid,
        String originalTransactionUuid,
        String fromWalletAddress,
        String toWalletAddress,
        BigDecimal amount) {}
