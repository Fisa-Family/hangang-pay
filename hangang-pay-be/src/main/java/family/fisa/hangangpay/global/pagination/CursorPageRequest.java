package family.fisa.hangangpay.global.pagination;

import java.time.LocalDateTime;

/** Cursor 페이징 요청 파라미터 */
public record CursorPageRequest(LocalDateTime cursorCreatedAt, Long cursorId) {}
