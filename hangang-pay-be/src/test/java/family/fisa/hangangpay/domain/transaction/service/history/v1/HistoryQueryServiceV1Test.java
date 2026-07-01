package family.fisa.hangangpay.domain.transaction.service.history.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.dto.user.response.AllHistoryItem;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.dto.UserHistoryType;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;

@ExtendWith(MockitoExtension.class)
class HistoryQueryServiceV1Test {

    @Mock MerchantRepository merchantRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock PaginationService paginationService;

    @InjectMocks HistoryQueryServiceV1 historyQueryService;

    private static final Long PARTY_ID = 10L;
    private static final Long MERCHANT_PARTY_ID = 20L;
    private static final int PAGE_SIZE = 10;

    private CursorPageRequest emptyRequest() {
        return new CursorPageRequest(null, null);
    }

    private Party party(Long id) {
        return Party.builder().id(id).partyType(PartyType.USER).build();
    }

    private Party merchantParty(Long id) {
        return Party.builder().id(id).partyType(PartyType.MERCHANT).build();
    }

    private Merchant merchant(Long partyId, String name) {
        return Merchant.builder().party(merchantParty(partyId)).merchantName(name).build();
    }

    private Transaction mockTx(Long id, Party fromParty, Party toParty, TransactionType type) {
        Transaction tx = mock(Transaction.class);
        lenient().when(tx.getId()).thenReturn(id);
        lenient().when(tx.getTransactionUuid()).thenReturn("transaction-uuid-%d".formatted(id));
        lenient().when(tx.getApprovalNumber()).thenReturn("APV-2026-%08d".formatted(id));
        lenient().when(tx.getFromParty()).thenReturn(fromParty);
        lenient().when(tx.getToParty()).thenReturn(toParty);
        lenient().when(tx.getTransactionType()).thenReturn(type);
        lenient().when(tx.getStatus()).thenReturn(TransactionStatus.SUCCESS);
        lenient().when(tx.getAmount()).thenReturn(new BigDecimal("10000"));
        lenient().when(tx.getCreatedAt()).thenReturn(LocalDateTime.now());
        lenient().when(tx.getUpdatedAt()).thenReturn(LocalDateTime.now());
        return tx;
    }

    @Nested
    @DisplayName("전체 내역 조회 (getAllHistories)")
    class GetAllHistories {

        @Test
        @DisplayName("정상: 4개 타입을 한 번에 조회하고 타입별로 AllHistoryItem 필드를 매핑")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void success_mapsEachType() {
            Transaction payment =
                    mockTx(
                            1L,
                            party(PARTY_ID),
                            merchantParty(MERCHANT_PARTY_ID),
                            TransactionType.PAYMENT);
            Transaction charge = mockTx(2L, party(PARTY_ID), null, TransactionType.CHARGE);
            when(charge.getDiscountAmount()).thenReturn(new BigDecimal("5000"));
            when(charge.getDiscountRate()).thenReturn(new BigDecimal("0.10"));
            Transaction exchange = mockTx(3L, party(PARTY_ID), null, TransactionType.EXCHANGE);

            Window<Transaction> window =
                    Window.from(List.of(payment, charge, exchange), i -> ScrollPosition.offset(i));

            when(paginationService.resolveScrollPosition(any()))
                    .thenReturn(ScrollPosition.offset());
            when(transactionRepository.findTransactionByPartyId(
                            eq(PARTY_ID),
                            eq(TransactionStatus.SUCCESS),
                            eq(
                                    List.of(
                                            TransactionType.PAYMENT,
                                            TransactionType.CANCEL,
                                            TransactionType.CHARGE,
                                            TransactionType.EXCHANGE)),
                            any(ScrollPosition.class),
                            any(Limit.class)))
                    .thenReturn(window);
            when(merchantRepository.findByParty_IdIn(List.of(MERCHANT_PARTY_ID)))
                    .thenReturn(List.of(merchant(MERCHANT_PARTY_ID, "카페 드롭탑")));

            CursorPageResponse sentinel = mock(CursorPageResponse.class);
            when(paginationService.toCursorPage(any()))
                    .thenAnswer(
                            invocation -> {
                                Window<AllHistoryItem> responseWindow = invocation.getArgument(0);
                                List<AllHistoryItem> content = responseWindow.getContent();

                                assertThat(content).hasSize(3);

                                // 결제: 가맹점명 + 승인번호 채움, 할인 필드 null
                                AllHistoryItem p = content.get(0);
                                assertThat(p.historyId()).isEqualTo(1L);
                                assertThat(p.historyType()).isEqualTo(UserHistoryType.PAYMENT);
                                assertThat(p.merchantName()).isEqualTo("카페 드롭탑");
                                assertThat(p.approvalNumber()).isEqualTo("APV-2026-00000001");
                                assertThat(p.discountAmount()).isNull();
                                assertThat(p.discountRate()).isNull();

                                // 충전: 할인 필드 채움, 가맹점명/승인번호 null
                                AllHistoryItem c = content.get(1);
                                assertThat(c.historyId()).isEqualTo(2L);
                                assertThat(c.historyType()).isEqualTo(UserHistoryType.CHARGE);
                                assertThat(c.merchantName()).isNull();
                                assertThat(c.approvalNumber()).isNull();
                                assertThat(c.discountAmount()).isEqualByComparingTo("5000");
                                assertThat(c.discountRate()).isEqualByComparingTo("0.10");

                                // 환전: amount/status/createdAt 외 전부 null
                                AllHistoryItem e = content.get(2);
                                assertThat(e.historyId()).isEqualTo(3L);
                                assertThat(e.historyType()).isEqualTo(UserHistoryType.EXCHANGE);
                                assertThat(e.merchantName()).isNull();
                                assertThat(e.approvalNumber()).isNull();
                                assertThat(e.discountAmount()).isNull();
                                assertThat(e.discountRate()).isNull();

                                return sentinel;
                            });

            CursorPageResponse result =
                    historyQueryService.getAllHistories(PARTY_ID, emptyRequest(), PAGE_SIZE);

            assertThat(result).isSameAs(sentinel);
            verify(transactionRepository)
                    .findTransactionByPartyId(
                            eq(PARTY_ID),
                            eq(TransactionStatus.SUCCESS),
                            eq(
                                    List.of(
                                            TransactionType.PAYMENT,
                                            TransactionType.CANCEL,
                                            TransactionType.CHARGE,
                                            TransactionType.EXCHANGE)),
                            any(ScrollPosition.class),
                            any(Limit.class));
        }

        @Test
        @DisplayName("가맹점 매핑 없음 -> 결제 원소 merchantName 이 '알 수 없는 가맹점' fallback")
        @SuppressWarnings({"rawtypes", "unchecked"})
        void missingMerchant_fallback() {
            Transaction payment =
                    mockTx(
                            1L,
                            party(PARTY_ID),
                            merchantParty(MERCHANT_PARTY_ID),
                            TransactionType.PAYMENT);
            Window<Transaction> window =
                    Window.from(List.of(payment), i -> ScrollPosition.offset(i));

            when(paginationService.resolveScrollPosition(any()))
                    .thenReturn(ScrollPosition.offset());
            when(transactionRepository.findTransactionByPartyId(
                            eq(PARTY_ID),
                            eq(TransactionStatus.SUCCESS),
                            any(),
                            any(ScrollPosition.class),
                            any(Limit.class)))
                    .thenReturn(window);
            when(merchantRepository.findByParty_IdIn(List.of(MERCHANT_PARTY_ID)))
                    .thenReturn(List.of());

            CursorPageResponse sentinel = mock(CursorPageResponse.class);
            when(paginationService.toCursorPage(any()))
                    .thenAnswer(
                            invocation -> {
                                Window<AllHistoryItem> responseWindow = invocation.getArgument(0);
                                assertThat(responseWindow.getContent().get(0).merchantName())
                                        .isEqualTo("알 수 없는 가맹점");
                                return sentinel;
                            });

            historyQueryService.getAllHistories(PARTY_ID, emptyRequest(), PAGE_SIZE);

            verify(merchantRepository).findByParty_IdIn(List.of(MERCHANT_PARTY_ID));
        }
    }
}
