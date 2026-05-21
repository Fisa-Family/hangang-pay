package family.fisa.hangangpaybank.domain.transaction.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CancelResponse(
        String transactionUuid,
        String originalTransactionUuid,
        String txHash,
        Long blockNumber,
        LocalDateTime confirmedAt,
        BigDecimal fromBalance,
        BigDecimal toBalance) {}
