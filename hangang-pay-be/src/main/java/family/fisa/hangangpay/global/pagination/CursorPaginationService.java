package family.fisa.hangangpay.global.pagination;

import org.springframework.data.domain.Window;
import org.springframework.stereotype.Service;

@Service
public class CursorPaginationService implements PaginationService {

    @Override
    public <T extends CursorItem> CursorPageResponse<T> toCursorPage(Window<T> window) {
        return null;
    }
}
