package family.fisa.hangangpay.global.pagination;

import java.time.LocalDateTime;

/** Cursor Pagination 응답 DTO 구현 인터페이스 */
public interface CursorItem {

    // 정렬키 1: 생성 시각
    LocalDateTime getCursorCreatedAt();

    // 정렬키 2: 엔티티 Id
    Long getCursorId();
}
