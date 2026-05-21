package family.fisa.hangangpaybank.domain.transaction.dto.request;

import java.math.BigDecimal;

public record ChargeRequest(
        String transactionUuid,
        Long institutionId,
        String accountNumber,
        String walletAddress,
        BigDecimal amount) {}
