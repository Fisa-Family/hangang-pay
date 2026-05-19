package family.fisa.hangangpay.domain.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserHistoryResponse<T>(
        UserHistoryType historyType,
        List<T> items,
        boolean hasNext,
        LocalDateTime nextCursorCreatedAt,
        Long nextCursorId) {

    public static <T> UserHistoryResponse<T> of(
            UserHistoryType type,
            List<T> items,
            boolean hasNext,
            LocalDateTime nextCursorCreatedAt,
            Long nextCursorId) {
        return new UserHistoryResponse<>(
                type,
                items,
                hasNext,
                hasNext ? nextCursorCreatedAt : null,
                hasNext ? nextCursorId : null);
    }
}
