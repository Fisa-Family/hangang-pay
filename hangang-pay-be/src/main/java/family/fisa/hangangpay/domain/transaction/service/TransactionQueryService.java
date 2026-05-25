package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.domain.merchant.dto.MerchantSettlementHistoryItem;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.UserChargeHistoryDetail;
import family.fisa.hangangpay.domain.transaction.dto.response.UserExchangeHistoryDetail;
import family.fisa.hangangpay.domain.transaction.dto.response.UserPaymentHistoryDetail;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
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
public class TransactionQueryService {

    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;
    private final PaginationService paginationService;

    /** 가게 결제 정보 가져오기 */
    public CursorPageResponse<PaymentHistoryItem> getUserPaymentHistory(
            Long partyId, CursorPageRequest request, int size) {
        log.info("결제 내역 조회 시작. partyId={}", partyId);
        ScrollPosition position = paginationService.resolveScrollPosition(request);

        // 1. PAYMENT Type의 Transaction 가져오기
        Window<Transaction> window =
                transactionRepository.findTransactionByPartyId(
                        partyId,
                        TransactionStatus.SUCCESS,
                        List.of(TransactionType.PAYMENT, TransactionType.CANCEL),
                        position,
                        Limit.of(size));

        // 2. 상대방 Party Id 가져오기
        List<Long> toPartyIds =
                window.getContent().stream().map(t -> t.getToParty().getId()).toList();

        // 3. 가져온 상대방 ID 기반으로 가맹점 조회
        Map<Long, String> merchantNameMap =
                merchantRepository.findByParty_IdIn(toPartyIds).stream()
                        .collect(
                                Collectors.toMap(
                                        m -> m.getParty().getId(), Merchant::getMerchantName));

        // 4. PaymentHistoryItem 변환
        Window<PaymentHistoryItem> responseWindow =
                window.map(
                        t -> {
                            Long payeePartyId = t.getToParty().getId();
                            String merchantName = merchantNameMap.get(payeePartyId);

                            if (merchantName == null) {
                                log.warn(
                                        "가맹점 정보 없음. transactionId={}, payeePartyId={}",
                                        t.getId(),
                                        payeePartyId);
                                merchantName = "알 수 없는 가맹점";
                            }

                            return PaymentHistoryItem.from(t, merchantName);
                        });

        log.info("결제 내역 조회 완료. partyId={}, count={}", partyId, window.getContent().size());
        return paginationService.toCursorPage(responseWindow);
    }

    /** 사용자 충전 정보 가져오기 */
    public CursorPageResponse<ChargeHistoryItem> getChargeHistories(
            Long partyId, CursorPageRequest request, int size) {
        log.info("충전 내역 조회 시작. partyId={}", partyId);
        ScrollPosition position = paginationService.resolveScrollPosition(request);

        // 1. CHARGE Type의 Transaction 가져오기
        Window<Transaction> transactions =
                transactionRepository.findTransactionByPartyId(
                        partyId,
                        TransactionStatus.SUCCESS,
                        List.of(TransactionType.CHARGE),
                        position,
                        Limit.of(size));

        // 2. ChargeHistoryItem 으로 변경
        Window<ChargeHistoryItem> window = transactions.map(ChargeHistoryItem::from);

        log.info("충전 내역 조회 완료. partyId={}, count={}", partyId, window.getContent().size());
        return paginationService.toCursorPage(window);
    }

    /** 사용자 환전 정보 가져오기 */
    public CursorPageResponse<ExchangeHistoryItem> getExchangeHistories(
            Long partyId, CursorPageRequest request, int size) {
        log.info("환전 내역 조회 시작. partyId={}", partyId);
        ScrollPosition position = paginationService.resolveScrollPosition(request);

        // 1. EXCHANGE Type의 Transaction 가져오기
        Window<Transaction> transactions =
                transactionRepository.findTransactionByPartyId(
                        partyId,
                        TransactionStatus.SUCCESS,
                        List.of(TransactionType.EXCHANGE),
                        position,
                        Limit.of(size));

        // 2. ExchangeHistoryItem 으로 변경
        Window<ExchangeHistoryItem> window = transactions.map(ExchangeHistoryItem::from);

        log.info("환전 내역 조회 완료. partyId={}, count={}", partyId, window.getContent().size());
        return paginationService.toCursorPage(window);
    }

    /** 사용자 결제 상세 내역 조회 */
    public UserPaymentHistoryDetail getUserPaymentHistoryDetail(Long partyId, Long transactionId) {
        // 1. Transaction 조회
        Transaction transaction =
                transactionRepository
                        .findDetailByIdAndTypes(
                                transactionId,
                                List.of(TransactionType.PAYMENT, TransactionType.CANCEL))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.PAYMENT_NOT_FOUND));

        // 2. 소유주 검증
        verifyOwner(partyId, transaction);

        return UserPaymentHistoryDetail.from(transaction);
    }

    /** 사용자 충전 상세 내역 조회 */
    public UserChargeHistoryDetail getUserChargeHistoryDetail(Long partyId, Long transactionId) {
        // 1. Transaction 조회
        Transaction transaction =
                transactionRepository
                        .findDetailByIdAndTypes(transactionId, List.of(TransactionType.CHARGE))
                        .orElseThrow(
                                () -> new BusinessException(TransactionErrorCode.CHARGE_NOT_FOUND));

        // 2. 소유주 검증
        verifyOwner(partyId, transaction);

        return UserChargeHistoryDetail.from(transaction);
    }

    /** 사용자 환전 상세 내역 조회 */
    public UserExchangeHistoryDetail getUserExchangeHistoryDetail(
            Long partyId, Long transactionId) {
        // 1. Transaction 조회
        Transaction transaction =
                transactionRepository
                        .findDetailByIdAndTypes(transactionId, List.of(TransactionType.EXCHANGE))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.EXCHANGE_NOT_FOUND));

        // 2. 소유주 검증
        verifyOwner(partyId, transaction);

        return UserExchangeHistoryDetail.from(transaction);
    }

    /** 조회자가 트랜잭션 발생자인지 검증 */
    private void verifyOwner(Long partyId, Transaction transaction) {
        if (!transaction.getFromParty().getId().equals(partyId)) {
            throw new BusinessException(UserErrorCode.NOT_OWNER);
        }
    }

    /** 가맹점 정산 내역 조회 */
    public CursorPageResponse<MerchantSettlementHistoryItem> getMerchantSettlementHistory(
            Long partyId, CursorPageRequest cursor, int size) {
        log.info("가맹점 정산 내역 조회 시작. partyId={}", partyId);
        ScrollPosition position = paginationService.resolveScrollPosition(cursor);

        Window<Transaction> transactions =
                transactionRepository.findTransactionByPartyId(
                        partyId,
                        TransactionStatus.SUCCESS,
                        List.of(TransactionType.EXCHANGE),
                        position,
                        Limit.of(size));

        Window<MerchantSettlementHistoryItem> window =
                transactions.map(MerchantSettlementHistoryItem::from);

        log.info("가맹점 정산 내역 조회 완료. partyId={}, count={}", partyId, window.getContent().size());
        return paginationService.toCursorPage(window);
    }
}
