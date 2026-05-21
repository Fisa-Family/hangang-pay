package family.fisa.hangangpaybank.domain.transaction.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        String transactionUuid,
        String txHash,
        Long blockNumber,
        LocalDateTime confirmedAt,
        BigDecimal fromBalance,
        BigDecimal toBalance) {}
