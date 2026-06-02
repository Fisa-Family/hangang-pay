package family.fisa.hangangpay.client.bank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        String transactionUuid,
        Long bankTransactionId,
        String txHash,
        Long blockNumber,
        LocalDateTime confirmedAt,
        BigDecimal fromBalance,
        BigDecimal toBalance) {}
