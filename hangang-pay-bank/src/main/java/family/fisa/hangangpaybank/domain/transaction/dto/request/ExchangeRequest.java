package family.fisa.hangangpaybank.domain.transaction.dto.request;

import java.math.BigDecimal;

public record ExchangeRequest(
        String transactionUuid,
        Long institutionId,
        String walletAddress,
        String accountNumber,
        BigDecimal amount) {}
