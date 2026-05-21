package family.fisa.hangangpay.client.bank.dto;

import java.math.BigDecimal;

public record ChargeRequest(
        String transactionUuid,
        Long institutionId,
        String accountNumber,
        String walletAddress,
        BigDecimal amount) {}
