package family.fisa.hangangpay.global.pagination;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cursor pagination 응답
 *
 * @param content 페이지 데이터 목록
 * @param nextCursorCreatedAt 다음 페이지 요청 시 사용할 cursor의 createdAt
 * @param nextCursorId 다음 페이지 요청 시 사용할 cursor의 ID
 * @param hasNext 다음 페이지 존재 여부
 * @param <T> CursorItem을 구현체
 */
public record CursorPageResponse<T>(
        List<T> content, LocalDateTime nextCursorCreatedAt, Long nextCursorId, boolean hasNext) {}
