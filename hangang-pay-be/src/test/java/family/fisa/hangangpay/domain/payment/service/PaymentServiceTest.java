package family.fisa.hangangpay.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.payment.dto.response.PaymentHistoryItem;
import family.fisa.hangangpay.domain.payment.entity.Payment;
import family.fisa.hangangpay.domain.payment.entity.PaymentStatus;
import family.fisa.hangangpay.domain.payment.repository.PaymentRepository;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @InjectMocks private PaymentQueryService paymentQueryService;

    @Mock private PaymentRepository paymentRepository;

    @Mock private MerchantRepository merchantRepository;

    @Mock private PaginationService paginationService;

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("첫 페이지 조회 테스트 - cursor 없음")
    void 첫_페이지_조회_테스트() {
        // given
        Long partyId = 1L;
        int size = 20;
        CursorPageRequest request = new CursorPageRequest(null, null);
        ScrollPosition position = ScrollPosition.keyset();

        Party payeeParty = mock(Party.class);
        when(payeeParty.getId()).thenReturn(2L);
        Payment payment1 = mock(Payment.class);
        when(payment1.getPayeeParty()).thenReturn(payeeParty);
        Payment payment2 = mock(Payment.class);
        when(payment2.getPayeeParty()).thenReturn(payeeParty);

        Window<Payment> paymentWindow = mock(Window.class);
        when(paymentWindow.getContent()).thenReturn(List.of(payment1, payment2));
        when(paymentWindow.map(any())).thenReturn(mock(Window.class));

        CursorPageResponse<PaymentHistoryItem> expectedResponse =
                new CursorPageResponse<>(
                        List.of(mock(PaymentHistoryItem.class), mock(PaymentHistoryItem.class)),
                        null,
                        null,
                        false);

        when(paginationService.resolveScrollPosition(request)).thenReturn(position);
        when(paymentRepository.findPaymentHistory(
                        partyId,
                        List.of(PaymentStatus.SUCCESS, PaymentStatus.CANCELLED),
                        Limit.of(size),
                        position))
                .thenReturn(paymentWindow);
        when(merchantRepository.findByPartyIdIn(any())).thenReturn(List.of());
        when(paginationService.toCursorPage(any()))
                .thenReturn((CursorPageResponse) expectedResponse);

        // when
        CursorPageResponse<PaymentHistoryItem> response =
                paymentQueryService.getUserPaymentHistory(partyId, request, size);

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursorId()).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("결제 내역 없음")
    void 결제_내역_없음() {
        // given
        Long partyId = 1L;
        int size = 20;
        CursorPageRequest request = new CursorPageRequest(null, null);
        ScrollPosition position = ScrollPosition.keyset();

        Window<Payment> paymentWindow = mock(Window.class);
        when(paymentWindow.getContent()).thenReturn(List.of());
        when(paymentWindow.map(any())).thenReturn(mock(Window.class));

        CursorPageResponse<PaymentHistoryItem> expectedResponse =
                new CursorPageResponse<>(List.of(), null, null, false);

        when(paginationService.resolveScrollPosition(request)).thenReturn(position);
        when(paymentRepository.findPaymentHistory(
                        partyId,
                        List.of(PaymentStatus.SUCCESS, PaymentStatus.CANCELLED),
                        Limit.of(size),
                        position))
                .thenReturn(paymentWindow);
        when(merchantRepository.findByPartyIdIn(any())).thenReturn(List.of());
        when(paginationService.toCursorPage(any()))
                .thenReturn((CursorPageResponse) expectedResponse);

        // when
        CursorPageResponse<PaymentHistoryItem> response =
                paymentQueryService.getUserPaymentHistory(partyId, request, size);

        // then
        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("취소된 결제 포함된 응답")
    void 취소된_결제_포함() {
        // given
        Long partyId = 1L;
        int size = 20;
        CursorPageRequest request = new CursorPageRequest(null, null);
        ScrollPosition position = ScrollPosition.keyset();

        Party payeeParty = mock(Party.class);
        when(payeeParty.getId()).thenReturn(2L);

        Payment cancelled = mock(Payment.class);
        when(cancelled.getStatus()).thenReturn(PaymentStatus.CANCELLED);
        when(cancelled.getPayeeParty()).thenReturn(payeeParty);
        when(cancelled.getApprovalNumber()).thenReturn("APV-2026-00000001");
        when(cancelled.getAmount()).thenReturn(BigDecimal.valueOf(10000));
        when(cancelled.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(cancelled.getId()).thenReturn(1L);

        Merchant merchant = mock(Merchant.class);
        when(merchant.getMerchantName()).thenReturn("테스트 가맹점");
        when(merchant.getParty()).thenReturn(payeeParty);

        Window<Payment> paymentWindow = mock(Window.class);
        when(paymentWindow.getContent()).thenReturn(List.of(cancelled));
        when(paymentWindow.map(any()))
                .thenAnswer(
                        inv -> {
                            java.util.function.Function<Payment, PaymentHistoryItem> fn =
                                    inv.getArgument(0);
                            PaymentHistoryItem mapped = fn.apply(cancelled);
                            Window<PaymentHistoryItem> rw = mock(Window.class);
                            when(rw.getContent()).thenReturn(List.of(mapped));
                            return rw;
                        });

        when(paginationService.resolveScrollPosition(request)).thenReturn(position);
        when(paymentRepository.findPaymentHistory(
                        partyId,
                        List.of(PaymentStatus.SUCCESS, PaymentStatus.CANCELLED),
                        Limit.of(size),
                        position))
                .thenReturn(paymentWindow);
        when(merchantRepository.findByPartyIdIn(List.of(2L))).thenReturn(List.of(merchant));
        when(paginationService.toCursorPage(any()))
                .thenAnswer(
                        inv -> {
                            Window<PaymentHistoryItem> w = inv.getArgument(0);
                            return new CursorPageResponse<>(w.getContent(), null, null, false);
                        });

        // when
        CursorPageResponse<PaymentHistoryItem> response =
                paymentQueryService.getUserPaymentHistory(partyId, request, size);

        // then
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).status()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("다음 페이지 있음 - hasNext=true")
    void 다음_페이지_존재() {
        // given
        Long partyId = 1L;
        int size = 20;
        CursorPageRequest request = new CursorPageRequest(null, null);
        ScrollPosition position = ScrollPosition.keyset();

        Party payeeParty2 = mock(Party.class);
        when(payeeParty2.getId()).thenReturn(2L);
        Payment payment = mock(Payment.class);
        when(payment.getPayeeParty()).thenReturn(payeeParty2);

        Window<Payment> paymentWindow = mock(Window.class);
        when(paymentWindow.getContent()).thenReturn(Collections.nCopies(size + 1, payment));
        when(paymentWindow.map(any())).thenReturn(mock(Window.class));

        CursorPageResponse<PaymentHistoryItem> expectedResponse =
                new CursorPageResponse<>(
                        Collections.nCopies(20, mock(PaymentHistoryItem.class)),
                        LocalDateTime.now(),
                        21L,
                        true);

        when(paginationService.resolveScrollPosition(request)).thenReturn(position);
        when(paymentRepository.findPaymentHistory(
                        partyId,
                        List.of(PaymentStatus.SUCCESS, PaymentStatus.CANCELLED),
                        Limit.of(size),
                        position))
                .thenReturn(paymentWindow);
        when(merchantRepository.findByPartyIdIn(any())).thenReturn(List.of());
        when(paginationService.toCursorPage(any()))
                .thenReturn((CursorPageResponse) expectedResponse);

        // when
        CursorPageResponse<PaymentHistoryItem> response =
                paymentQueryService.getUserPaymentHistory(partyId, request, size);

        // then
        assertThat(response.content()).hasSize(20);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursorId()).isNotNull();
    }
}
