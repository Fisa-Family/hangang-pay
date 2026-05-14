package family.fisa.hangangpay.global.pagination;

import org.springframework.data.domain.Window;

/** 페이지네이션 변환 */
public interface PaginationService {

    /** DTO로 매핑된 Window를 CursorPageResponse로 변환 */
    <T extends CursorItem> CursorPageResponse<T> toCursorPage(Window<T> window);
}
