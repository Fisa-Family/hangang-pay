package family.fisa.hangangpay.domain.transaction.service.history.v1;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.dto.user.response.AllHistoryItem;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.history.HistoryQueryService;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoryQueryServiceV1 implements HistoryQueryService {

    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;
    private final PaginationService paginationService;

    @Override
    public CursorPageResponse<AllHistoryItem> getAllHistories(
            Long partyId, CursorPageRequest request, int size) {
        log.info("전체 내역 조회 시작. partyId={}", partyId);
        ScrollPosition position = paginationService.resolveScrollPosition(request);

        /** 1. 모든 타입 Transaction을 단일 커서 쿼리로 조회한다. */
        Window<Transaction> window =
                transactionRepository.findTransactionByPartyId(
                        partyId,
                        TransactionStatus.SUCCESS,
                        List.of(
                                TransactionType.PAYMENT,
                                TransactionType.CANCEL,
                                TransactionType.CHARGE,
                                TransactionType.EXCHANGE),
                        position,
                        Limit.of(size));

        /** 2. PAYMENT/CANCEL 행의 가맹점명 일괄 조회 + PartyId : Name을 Map으로 매핑 */
        List<Long> payeePartyIds =
                window.getContent().stream()
                        .filter(t -> isPaymentLike(t.getTransactionType()))
                        .map(t -> t.getToParty().getId())
                        .distinct()
                        .toList();

        Map<Long, String> merchantNames =
                merchantRepository.findByParty_IdIn(payeePartyIds).stream()
                        .collect(
                                Collectors.toMap(
                                        m -> m.getParty().getId(), Merchant::getMerchantName));

        /** 3. 타입별 매핑, (PAYMENT / CANCEL 만 가맹점명을 주입한다. -> 없을 시, fallback */
        Window<AllHistoryItem> responseWindow =
                window.map(
                        t -> {
                            String merchantName = null;
                            if (isPaymentLike(t.getTransactionType())) {
                                merchantName =
                                        merchantNames.getOrDefault(
                                                t.getToParty().getId(), "알 수 없는 가맹점");
                            }
                            return AllHistoryItem.from(t, merchantName);
                        });

        log.info("전체 내역 조회 완료. partyId={}, count={}", partyId, window.getContent().size());

        return paginationService.toCursorPage(responseWindow);
    }

    /** 결제에 해당하는지 확인(PAYMENT/CANCEL) */
    private boolean isPaymentLike(TransactionType type) {
        return type == TransactionType.CANCEL || type == TransactionType.PAYMENT;
    }
}
