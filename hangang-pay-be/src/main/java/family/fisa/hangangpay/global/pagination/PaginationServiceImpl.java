package family.fisa.hangangpay.global.pagination;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Service;

@Service
public class PaginationServiceImpl implements PaginationService {

    @Override
    public <T extends CursorItem> CursorPageResponse<T> toCursorPage(Window<T> window) {
        List<T> content = window.getContent();
        boolean hasNext = window.hasNext();

        if (!hasNext) {
            return new CursorPageResponse<>(content, null, null, false);
        }

        // 내부적으로 20 + 1 개를 주니, - 1로 20개를 맞춘다.
        T last = content.get(content.size() - 1);

        return new CursorPageResponse<>(
                content, last.getCursorCreatedAt(), last.getCursorId(), true);
    }

    /** ScrollPosition: 어디서부터 데이터를 가져올지 알려주는 책갈피 * */
    @Override
    public ScrollPosition resolveScrollPosition(CursorPageRequest request) {

        // 첫 페이지 요청이다? -> 처음부터 준다.
        if (request.cursorCreatedAt() == null) {
            return ScrollPosition.keyset();
        }

        // cursor가 있다면, Map 형태로 다음페이지를 요청한다.
        // 이 시각, 이 id 이후 데이터를 보여달라고 ScrollPosition을 만들어준다.
        return ScrollPosition.of(
                // 엔티티 필드명
                Map.of(
                        "createdAt", request.cursorCreatedAt(), // 마지막으로 본 항목의 시각
                        "id", request.cursorId() // 마지막으로 본 항목의 id
                        ),
                KeysetScrollPosition.Direction.FORWARD // 이어서 가져오기
                );
    }
}
