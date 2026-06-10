package family.fisa.hangangpay.client.bank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CancelResponse(
        String transactionUuid,
        String originalTransactionUuid,
        String status,
        LocalDateTime confirmedAt,
        BigDecimal fromBalance,
        BigDecimal toBalance) {}
