package family.fisa.hangangpay.client.bank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExchangeResponse(
        String transactionUuid,
        Long bankTransactionId,
        String txHash,
        Long blockNumber,
        LocalDateTime confirmedAt,
        BigDecimal accountBalance) {}
