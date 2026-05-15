package family.fisa.hangangpay.domain.transfer.service;

import family.fisa.hangangpay.domain.transfer.dto.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transfer.repository.FundTransferRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FundTransferService {

    private static final int PAGE_SIZE = 20;

    private final UserRepository userRepository;
    private final FundTransferRepository fundTransferRepository;
    private final PaginationService paginationService;

    /** FundTransfer 내부 CHARGE 타입 내역 조회 */
    public CursorPageResponse<ChargeHistoryItem> getChargeHistories(
            Long userId, CursorPageRequest request) {

        // 1. userId partyId 변환
        Long partyId =
                userRepository
                        .findPartyIdByUserId(userId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        // 2. cursor ScrollPosition 변환
        ScrollPosition position = toScrollPosition(request);

        // 3. 충전 내역 조회
        Window<ChargeHistoryItem> window =
                fundTransferRepository.findChargeHistoriesByPartyId(
                        partyId, position, Limit.of(PAGE_SIZE));

        // 4. CursorPageResponse 변환 위임
        return paginationService.toCursorPage(window);
    }

    private ScrollPosition toScrollPosition(CursorPageRequest request) {
        // 1. null 이면 initial return
        if (request.cursorCreatedAt() == null) {
            return ScrollPosition.keyset();
        }
        // 2. createdAt, id 다음 위치 return
        return ScrollPosition.forward(
                Map.of(
                        "createdAt", request.cursorCreatedAt(),
                        "id", request.cursorId()));
    }
}
