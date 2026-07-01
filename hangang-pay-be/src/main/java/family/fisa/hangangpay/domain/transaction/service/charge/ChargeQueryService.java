package family.fisa.hangangpay.domain.transaction.service.charge;

import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeInitResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.UserChargeHistoryDetail;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;

/** CHARGE(충전) 조회 전용. */
public interface ChargeQueryService {

    /** 잔액, 월 한도, 계좌 목록을 조합해 충전 초기화 응답 반환 */
    ChargeInitResponse getChargeInit(Long partyId);

    /** 사용자 충전 내역 커서 페이지 */
    CursorPageResponse<ChargeHistoryItem> getChargeHistories(
            Long partyId, CursorPageRequest request, int size);

    /** 사용자 충전 상세 내역 */
    UserChargeHistoryDetail getUserChargeHistoryDetail(Long partyId, Long transactionId);
}
