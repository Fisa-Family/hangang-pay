package family.fisa.hangangpay.global.pagination;

import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Cursor 페이징 요청 파라미터
 */
public record CursorPageRequest(

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
    Long cursorId) {

}
