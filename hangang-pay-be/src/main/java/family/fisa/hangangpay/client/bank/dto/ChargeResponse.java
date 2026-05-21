package family.fisa.hangangpay.client.bank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChargeResponse(
        String transactionUuid,
        Long bankTransactionId,
        String txHash,
        Long blockNumber,
        LocalDateTime confirmedAt,
        BigDecimal walletBalance) {}
