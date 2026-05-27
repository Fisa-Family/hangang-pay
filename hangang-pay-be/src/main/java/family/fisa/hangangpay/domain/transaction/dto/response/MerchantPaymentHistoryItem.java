package family.fisa.hangangpay.domain.transaction.dto.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.global.pagination.CursorItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MerchantPaymentHistoryItem(
        Long transactionId,
        String approvalNumber,
        String payerName,
        BigDecimal amount,
        TransactionType transactionType,
        LocalDateTime createdAt)
        implements CursorItem {

    @Override
    public LocalDateTime getCursorCreatedAt() {
        return createdAt;
    }

    @Override
    public Long getCursorId() {
        return transactionId;
    }

    public static MerchantPaymentHistoryItem from(Transaction transaction, String payerName) {
        return new MerchantPaymentHistoryItem(
                transaction.getId(),
                transaction.getApprovalNumber(),
                UsernameMasker.mask(payerName),
                transaction.getAmount(),
                transaction.getTransactionType(),
                transaction.getCreatedAt());
    }
}
