package family.fisa.hangangpay.domain.transaction.service.charge.v1;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.wallet.service.WalletQueryService;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
class ChargeQueryServiceV1Test {

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock WalletQueryService walletQueryService;
    @Mock PaginationService paginationService;

    @InjectMocks ChargeQueryServiceV1 chargeQueryService;

    private static final Long PARTY_ID = 10L;
    private static final Long OTHER_PARTY_ID = 99L;
    private static final Long TRANSACTION_ID = 1L;
    private static final int PAGE_SIZE = 10;

    private CursorPageRequest emptyRequest() {
        return new CursorPageRequest(null, null);
    }

    private Party party(Long id) {
        return Party.builder().id(id).partyType(PartyType.USER).build();
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
    @DisplayName("충전 내역 조회 (getChargeHistories)")
    class GetChargeHistories {

        @Test
        @DisplayName("정상: CHARGE 타입을 status=SUCCESS로 조회")
        void success() {
            Window<Transaction> empty = Window.from(List.of(), i -> ScrollPosition.offset(i));
            when(paginationService.resolveScrollPosition(any()))
                    .thenReturn(ScrollPosition.offset());
            when(transactionRepository.findTransactionByPartyId(
                            eq(PARTY_ID),
                            eq(TransactionStatus.SUCCESS),
                            eq(List.of(TransactionType.CHARGE)),
                            any(ScrollPosition.class),
                            any(Limit.class)))
                    .thenReturn(empty);
            when(paginationService.toCursorPage(any())).thenReturn(mock(CursorPageResponse.class));

            chargeQueryService.getChargeHistories(PARTY_ID, emptyRequest(), PAGE_SIZE);

            verify(transactionRepository)
                    .findTransactionByPartyId(
                            eq(PARTY_ID),
                            eq(TransactionStatus.SUCCESS),
                            eq(List.of(TransactionType.CHARGE)),
                            any(ScrollPosition.class),
                            any(Limit.class));
        }
    }

    @Nested
    @DisplayName("충전 상세 조회 (getUserChargeHistoryDetail)")
    class GetUserChargeHistoryDetail {

        @Test
        @DisplayName("Transaction 없음 -> CHARGE_NOT_FOUND")
        void throws_whenNotFound() {
            when(transactionRepository.findDetailByIdAndTypes(
                            eq(TRANSACTION_ID), eq(List.of(TransactionType.CHARGE))))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    chargeQueryService.getUserChargeHistoryDetail(
                                            PARTY_ID, TRANSACTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", TransactionErrorCode.CHARGE_NOT_FOUND);
        }

        @Test
        @DisplayName("소유자 불일치 -> NOT_OWNER")
        void throws_whenNotOwner() {
            Transaction tx =
                    mockTx(TRANSACTION_ID, party(OTHER_PARTY_ID), null, TransactionType.CHARGE);
            when(transactionRepository.findDetailByIdAndTypes(eq(TRANSACTION_ID), any()))
                    .thenReturn(Optional.of(tx));

            assertThatThrownBy(
                            () ->
                                    chargeQueryService.getUserChargeHistoryDetail(
                                            PARTY_ID, TRANSACTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", UserErrorCode.NOT_OWNER);
        }
    }
}
