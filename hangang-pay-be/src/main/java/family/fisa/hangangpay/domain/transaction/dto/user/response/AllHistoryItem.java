package family.fisa.hangangpay.domain.transaction.dto.user.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.user.dto.UserHistoryType;
import family.fisa.hangangpay.global.pagination.CursorItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AllHistoryItem(
        Long historyId,
        UserHistoryType historyType,
        String merchantName, // PAYMENT/CANCEL 만 (toParty 가맹점명)
        BigDecimal amount,
        String approvalNumber, // PAYMENT/CANCEL 만
        BigDecimal discountAmount, // CHARGE 만
        BigDecimal discountRate, // CHARGE 만
        TransactionStatus status,
        LocalDateTime createdAt)
        implements CursorItem {

    public static AllHistoryItem from(Transaction t, String merchantName) {
        UserHistoryType type = toHistoryType(t.getTransactionType());

        return switch (type) {
            case PAYMENT, CANCEL ->
                    new AllHistoryItem(
                            t.getId(),
                            type,
                            merchantName,
                            t.getAmount(),
                            t.getApprovalNumber(),
                            null,
                            null,
                            t.getStatus(),
                            t.getCreatedAt());
            case CHARGE ->
                    new AllHistoryItem(
                            t.getId(),
                            type,
                            null,
                            t.getAmount(),
                            null,
                            t.getDiscountAmount(),
                            t.getDiscountRate(),
                            t.getStatus(),
                            t.getCreatedAt());
            case EXCHANGE ->
                    new AllHistoryItem(
                            t.getId(),
                            type,
                            null,
                            t.getAmount(),
                            null,
                            null,
                            null,
                            t.getStatus(),
                            t.getCreatedAt());
            case ALL ->
                    throw new IllegalStateException(
                            "transaction 단건은 ALL 타입일 수 없다"); // 의도한 오류가 아닌, 로직 버그 (컴파일을 위해 추가)
        };
    }

    private static UserHistoryType toHistoryType(TransactionType t) {
        return switch (t) {
            case PAYMENT -> UserHistoryType.PAYMENT;
            case CANCEL -> UserHistoryType.CANCEL;
            case CHARGE -> UserHistoryType.CHARGE;
            case EXCHANGE -> UserHistoryType.EXCHANGE;
        };
    }

    @Override
    public LocalDateTime getCursorCreatedAt() {
        return createdAt;
    }

    @Override
    public Long getCursorId() {
        return historyId;
    }
}
