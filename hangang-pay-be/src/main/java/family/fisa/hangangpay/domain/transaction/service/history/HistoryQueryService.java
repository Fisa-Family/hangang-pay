package family.fisa.hangangpay.domain.transaction.service.history;

import family.fisa.hangangpay.domain.transaction.dto.user.response.AllHistoryItem;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;

/** 전 플로우(PAYMENT/CANCEL/CHARGE/EXCHANGE) 통합 내역 조회 전용. */
public interface HistoryQueryService {

    CursorPageResponse<AllHistoryItem> getAllHistories(
            Long partyId, CursorPageRequest request, int size);
}
