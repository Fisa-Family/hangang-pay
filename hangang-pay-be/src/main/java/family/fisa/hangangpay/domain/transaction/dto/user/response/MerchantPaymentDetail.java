package family.fisa.hangangpay.domain.transaction.dto.user.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MerchantPaymentDetail(
        Long transactionId,
        TransactionType transactionType,
        BigDecimal amount,
        String payerName,
        String approvalNumber,
        String paymentStatus,
        LocalDateTime createdAt,
        boolean cancelAvailable) {

    public static MerchantPaymentDetail from(
            Transaction transaction, String payerName, boolean cancelAvailable) {
        return new MerchantPaymentDetail(
                transaction.getId(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                UsernameMasker.mask(payerName),
                transaction.getApprovalNumber(),
                transaction.getStatus().name(),
                transaction.getCreatedAt(),
                cancelAvailable);
    }
}
