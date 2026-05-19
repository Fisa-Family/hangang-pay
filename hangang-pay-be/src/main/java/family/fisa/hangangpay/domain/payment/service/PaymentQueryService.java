package family.fisa.hangangpay.domain.payment.service;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.payment.dto.response.PaymentHistoryItem;
import family.fisa.hangangpay.domain.payment.entity.Payment;
import family.fisa.hangangpay.domain.payment.entity.PaymentStatus;
import family.fisa.hangangpay.domain.payment.repository.PaymentRepository;
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
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final MerchantRepository merchantRepository;
    private final PaginationService paginationService;

    public CursorPageResponse<PaymentHistoryItem> getUserPaymentHistory(
            Long partyId, CursorPageRequest request, int size) {

        log.info("결제 내역 조회 시작. partyId={}", partyId);

        /**
         * 1. 어디서 가져올지 파악한다. (책갈피 역할) request의 createdAt과 id를 기준 삼는다. Spring Data Jpa는 Map을 이해하지
         * 못한다. 따라서 ScrollPosition이 파라미터를 Map형태로 가지고, 쿼리 메소드의 파라미터로 가질 수 있도록 추상화 해준다.
         */
        ScrollPosition position = paginationService.resolveScrollPosition(request);

        /**
         * 2. window 객체 반환 position을 기반으로 결제자(Users)의 지불 내역에 대한 Payment 객체를 가져온다.
         *
         * <p>이때 Window타입은 기존 List와 다르게 다음 row가 존재하는지를 판단하는 hasNext()를 가진다.
         *
         * <p>결론적으로 (partyId, 최대로 가져올 사이즈, 책갈피 위치) 를 가지고 조회를 하게된다.
         */
        Window<Payment> window =
                paymentRepository.findPaymentHistory(
                        partyId,
                        List.of(PaymentStatus.SUCCESS, PaymentStatus.CANCELLED),
                        Limit.of(size),
                        position);

        /**
         * 3. Merchant의 PartyId 추출
         *
         * <p>수취자(Merchant)의 Id를 가져오기 위해 결제자(Users)의 지불 내역을 기준으로 수취자(Merchant)의 PartyId를 리스트로 추출한다.
         */
        List<Long> payeePartyIds =
                window.getContent().stream().map(p -> p.getPayeeParty().getId()).toList();

        /** 4. Merchant의 PartyId 기반으로 MerchantName 추출 */
        Map<Long, String> merchantNameMap =
                merchantRepository.findByParty_IdIn(payeePartyIds).stream()
                        .collect(
                                Collectors.toMap(
                                        m -> m.getParty().getId(), Merchant::getMerchantName));

        /** 5. paginationService.toCursorPage 형태에 맞게 response DTO 변환 */
        Window<PaymentHistoryItem> responseWindow =
                window.map(
                        p -> {
                            Long payeePartyId = p.getPayeeParty().getId();
                            String merchantName = merchantNameMap.get(payeePartyId);
                            if (merchantName == null) {
                                log.warn(
                                        "가맹점 정보 없음. paymentId={}, payeePartyId={}",
                                        p.getId(),
                                        payeePartyId);
                                merchantName = "알 수 없는 가맹점";
                            }
                            return PaymentHistoryItem.from(p, merchantName);
                        });

        log.info("결제 내역 조회 완료. partyId={}, count={}", partyId, window.getContent().size());

        /** <T extends CursorItem> CursorPageResponse<T> 형태로 반환 */
        return paginationService.toCursorPage(responseWindow);
    }
}
