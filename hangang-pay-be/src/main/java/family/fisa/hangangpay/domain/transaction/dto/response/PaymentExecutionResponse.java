package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentExecutionResponse(
    String transactionUuid,
    TransactionStatus status,
    String approvalNumber,
    String txHash,
    BigDecimal amount,
    String merchantName,
    LocalDateTime confirmedAt) {}
