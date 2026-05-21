package family.fisa.hangangpay.domain.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTx;
import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpay.domain.blockchain.entity.ReferenceType;
import family.fisa.hangangpay.domain.blockchain.repository.BlockchainTxRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.UserChargeHistoryDetail;
import family.fisa.hangangpay.domain.transaction.dto.response.UserExchangeHistoryDetail;
import family.fisa.hangangpay.domain.transfer.entity.FundTransfer;
import family.fisa.hangangpay.domain.transfer.entity.TransferStatus;
import family.fisa.hangangpay.domain.transfer.entity.TransferType;
import family.fisa.hangangpay.domain.transfer.repository.FundTransferRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
class FundTransferQueryServiceTest {

    @Mock UserRepository userRepository;
    @Mock FundTransferRepository fundTransferRepository;
    @Mock BlockchainTxRepository blockchainTxRepository;

    @Mock PaginationService paginationService;

    @InjectMocks FundTransferQueryService fundTransferQueryService;

    private static final Long PARTY_ID = 10L;
    private static final Long FT_ID = 7L;

    private FundTransfer fundTransfer(TransferType type, Long ownerPartyId) {
        Party owner = Party.builder().id(ownerPartyId).partyType(PartyType.USER).build();
        Institution institution =
                Institution.builder().id(1L).institutionCode("020").institutionName("우리은행").build();
        Account account =
                Account.builder()
                        .id(100L)
                        .party(owner)
                        .institution(institution)
                        .accountNumber("1002-123-456789")
                        .build();
        Wallet wallet =
                Wallet.builder()
                        .id(200L)
                        .party(owner)
                        .institution(institution)
                        .address("0xWallet")
                        .build();
        boolean isCharge = type == TransferType.CHARGE;
        return FundTransfer.builder()
                .id(FT_ID)
                .party(owner)
                .account(account)
                .wallet(wallet)
                .amount(new BigDecimal("50000"))
                .discountAmount(isCharge ? new BigDecimal("5000") : null)
                .discountRate(isCharge ? new BigDecimal("0.1000") : null)
                .status(TransferStatus.SUCCESS)
                .transferType(type)
                .build();
    }

    private BlockchainTx blockchainTx() {
        return BlockchainTx.builder()
                .referenceId(FT_ID)
                .referenceType(ReferenceType.FUND_TRANSFER)
                .txHash("0xdef")
                .status(BlockchainTxStatus.CONFIRMED)
                .build();
    }

    @Nested
    @DisplayName("충전 내역 조회 (getChargeHistories)")
    class ChargeHistories {

        @Test
        @DisplayName("사용자 없으면 USER_NOT_FOUND 예외")
        void throwsWhenUserNotFound() {
            // given
            when(userRepository.findPartyIdByUserId(1L)).thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(
                            () ->
                                    fundTransferQueryService.getChargeHistories(
                                            1L, new CursorPageRequest(null, null), 20))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("커서가 없으면 keyset() 초기 위치로 조회")
        void initialCursor() {
            // given
            Long userId = 1L, partyId = 1L;
            Window<ChargeHistoryItem> window = mock(Window.class);
            CursorPageResponse<ChargeHistoryItem> expected =
                    new CursorPageResponse<>(List.of(), null, null, false);

            when(userRepository.findPartyIdByUserId(userId)).thenReturn(Optional.of(partyId));
            when(paginationService.resolveScrollPosition(new CursorPageRequest(null, null)))
                    .thenReturn(ScrollPosition.keyset());
            when(fundTransferRepository.findChargeHistoriesByPartyId(
                            partyId, ScrollPosition.keyset(), Limit.of(20)))
                    .thenReturn(window);
            when(paginationService.toCursorPage(window)).thenReturn(expected);

            // when
            CursorPageResponse<ChargeHistoryItem> actual =
                    fundTransferQueryService.getChargeHistories(
                            userId, new CursorPageRequest(null, null), 20);

            // then
            assertThat(actual).isSameAs(expected);
            // CursorPageRequest.cursorCreatedAt == null 이면 ScrollPosition.keyset() 로 들어간다
            verify(fundTransferRepository)
                    .findChargeHistoriesByPartyId(partyId, ScrollPosition.keyset(), Limit.of(20));
        }

        @Test
        @DisplayName("커서가 존재하면 forward(createdAt, id) 위치로 조회")
        void forwardCursor() {
            // given
            Long userId = 1L, partyId = 1L, cursorId = 1L;
            LocalDateTime cursorAt = LocalDateTime.of(2026, 5, 15, 10, 0);
            ScrollPosition expectedPos =
                    ScrollPosition.forward(Map.of("createdAt", cursorAt, "id", cursorId));

            Window<ChargeHistoryItem> window = mock(Window.class);
            CursorPageResponse<ChargeHistoryItem> expected =
                    new CursorPageResponse<>(List.of(), null, null, false);

            when(userRepository.findPartyIdByUserId(userId)).thenReturn(Optional.of(partyId));
            when(paginationService.resolveScrollPosition(new CursorPageRequest(cursorAt, cursorId)))
                    .thenReturn(expectedPos);
            when(fundTransferRepository.findChargeHistoriesByPartyId(
                            partyId, expectedPos, Limit.of(20)))
                    .thenReturn(window);
            when(paginationService.toCursorPage(window)).thenReturn(expected);

            // when
            CursorPageResponse<ChargeHistoryItem> actual =
                    fundTransferQueryService.getChargeHistories(
                            userId, new CursorPageRequest(cursorAt, cursorId), 20);

            // then
            assertThat(actual).isSameAs(expected);
            verify(fundTransferRepository)
                    .findChargeHistoriesByPartyId(partyId, expectedPos, Limit.of(20));
        }
    }

    @Nested
    @DisplayName("환전 내역 조회 (getExchangeHistories)")
    class ExchangeHistories {

        @Test
        @DisplayName("사용자 없으면 USER_NOT_FOUND 예외")
        void throwsWhenUserNotFound() {
            // given
            when(userRepository.findPartyIdByUserId(1L)).thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(
                            () ->
                                    fundTransferQueryService.getExchangeHistories(
                                            1L, new CursorPageRequest(null, null), 20))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("커서가 없으면 keyset() 초기 위치로 조회")
        void initialCursor() {
            // given
            Long userId = 1L, partyId = 1L;
            Window<ExchangeHistoryItem> window = mock(Window.class);
            CursorPageResponse<ExchangeHistoryItem> expected =
                    new CursorPageResponse<>(List.of(), null, null, false);

            when(userRepository.findPartyIdByUserId(userId)).thenReturn(Optional.of(partyId));
            when(paginationService.resolveScrollPosition(new CursorPageRequest(null, null)))
                    .thenReturn(ScrollPosition.keyset());
            when(fundTransferRepository.findExchangeHistoriesByPartyId(
                            partyId, ScrollPosition.keyset(), Limit.of(20)))
                    .thenReturn(window);
            when(paginationService.toCursorPage(window)).thenReturn(expected);

            // when
            CursorPageResponse<ExchangeHistoryItem> actual =
                    fundTransferQueryService.getExchangeHistories(
                            userId, new CursorPageRequest(null, null), 20);

            // then
            assertThat(actual).isSameAs(expected);
            // CursorPageRequest.cursorCreatedAt == null 이면 ScrollPosition.keyset() 로 들어간다
            verify(fundTransferRepository)
                    .findExchangeHistoriesByPartyId(partyId, ScrollPosition.keyset(), Limit.of(20));
        }

        @Test
        @DisplayName("커서가 존재하면 forward(createdAt, id) 위치로 조회")
        void forwardCursor() {
            // given
            Long userId = 1L, partyId = 1L, cursorId = 1L;
            LocalDateTime cursorAt = LocalDateTime.of(2026, 5, 15, 10, 0);
            ScrollPosition expectedPos =
                    ScrollPosition.forward(Map.of("createdAt", cursorAt, "id", cursorId));

            Window<ExchangeHistoryItem> window = mock(Window.class);
            CursorPageResponse<ExchangeHistoryItem> expected =
                    new CursorPageResponse<>(List.of(), null, null, false);

            when(userRepository.findPartyIdByUserId(userId)).thenReturn(Optional.of(partyId));
            when(paginationService.resolveScrollPosition(new CursorPageRequest(cursorAt, cursorId)))
                    .thenReturn(expectedPos);
            when(fundTransferRepository.findExchangeHistoriesByPartyId(
                            partyId, expectedPos, Limit.of(20)))
                    .thenReturn(window);
            when(paginationService.toCursorPage(window)).thenReturn(expected);

            // when
            CursorPageResponse<ExchangeHistoryItem> actual =
                    fundTransferQueryService.getExchangeHistories(
                            userId, new CursorPageRequest(cursorAt, cursorId), 20);

            // then
            assertThat(actual).isSameAs(expected);
            verify(fundTransferRepository)
                    .findExchangeHistoriesByPartyId(partyId, expectedPos, Limit.of(20));
        }
    }

    @Nested
    @DisplayName("충전 상세 (getUserChargeHistoryDetail)")
    class ChargeDetail {
        @Test
        @DisplayName("정상: 본인 + CHARGE + blockchain_tx 있음")
        void success_withBlockchainTx() throws Exception {
            // given
            when(fundTransferRepository.findByIdWithAccountAndWallet(FT_ID))
                    .thenReturn(Optional.of(fundTransfer(TransferType.CHARGE, PARTY_ID)));
            when(blockchainTxRepository.findByReferenceTypeAndReferenceId(
                            ReferenceType.FUND_TRANSFER, FT_ID))
                    .thenReturn(Optional.of(blockchainTx()));

            // when
            UserChargeHistoryDetail result =
                    fundTransferQueryService.getUserChargeHistoryDetail(PARTY_ID, FT_ID);

            // then
            assertThat(result.historyId()).isEqualTo(FT_ID);
            assertThat(result.amount()).isEqualByComparingTo("50000");
            assertThat(result.discountAmount()).isEqualByComparingTo("5000");
            assertThat(result.discountRate()).isEqualByComparingTo("10.0");
            assertThat(result.actualPaidAmount()).isEqualByComparingTo("45000");
            assertThat(result.transferStatus()).isEqualTo("SUCCESS");
            assertThat(result.transferType()).isEqualTo("CHARGE");
            assertThat(result.accountNumber()).isEqualTo("1002-123-456789");
            assertThat(result.bankName()).isEqualTo("우리은행");
            assertThat(result.walletAddress()).isEqualTo("0xWallet");
            assertThat(result.txHash()).isEqualTo("0xdef");
            assertThat(result.blockchainStatus()).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("정상: blockchain_tx 없음 -> txHash/blockchainStatus = null")
        void success_withoutBlockchainTx() {
            // given
            when(fundTransferRepository.findByIdWithAccountAndWallet(FT_ID))
                    .thenReturn(Optional.of(fundTransfer(TransferType.CHARGE, PARTY_ID)));
            when(blockchainTxRepository.findByReferenceTypeAndReferenceId(
                            ReferenceType.FUND_TRANSFER, FT_ID))
                    .thenReturn(Optional.empty());

            // when
            UserChargeHistoryDetail result =
                    fundTransferQueryService.getUserChargeHistoryDetail(PARTY_ID, FT_ID);

            // then
            assertThat(result.txHash()).isNull();
            assertThat(result.blockchainStatus()).isNull();
        }

        @Test
        @DisplayName("fundTransfer 없음 -> HISTORY_NOT_FOUND")
        void throws_whenNotFound() {
            when(fundTransferRepository.findByIdWithAccountAndWallet(FT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    fundTransferQueryService.getUserChargeHistoryDetail(
                                            PARTY_ID, FT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", UserErrorCode.HISTORY_NOT_FOUND);
        }

        @Test
        @DisplayName("타입 불일치(EXCHANGE를 CHARGE로 조회) -> HISTORY_NOT_FOUND")
        void throws_whenTypeMismatch() {
            when(fundTransferRepository.findByIdWithAccountAndWallet(FT_ID))
                    .thenReturn(Optional.of(fundTransfer(TransferType.EXCHANGE, PARTY_ID)));

            assertThatThrownBy(
                            () ->
                                    fundTransferQueryService.getUserChargeHistoryDetail(
                                            PARTY_ID, FT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", UserErrorCode.HISTORY_NOT_FOUND);
        }

        @Test
        @DisplayName("소유자 불일치 -> NOT_OWNER")
        void throws_whenNotOwner() {
            Long otherPartyId = 999L;
            when(fundTransferRepository.findByIdWithAccountAndWallet(FT_ID))
                    .thenReturn(Optional.of(fundTransfer(TransferType.CHARGE, otherPartyId)));

            assertThatThrownBy(
                            () ->
                                    fundTransferQueryService.getUserChargeHistoryDetail(
                                            PARTY_ID, FT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", UserErrorCode.NOT_OWNER);
        }
    }

    @Nested
    @DisplayName("환전 상세 (getUserExchangeHistoryDetail)")
    class ExchangeDetail {

        @Test
        @DisplayName("정상: 본인 + EXCHANGE + blockchain_tx 있음")
        void success_withBlockchainTx() {
            when(fundTransferRepository.findByIdWithAccountAndWallet(FT_ID))
                    .thenReturn(Optional.of(fundTransfer(TransferType.EXCHANGE, PARTY_ID)));
            when(blockchainTxRepository.findByReferenceTypeAndReferenceId(
                            ReferenceType.FUND_TRANSFER, FT_ID))
                    .thenReturn(Optional.of(blockchainTx()));

            UserExchangeHistoryDetail result =
                    fundTransferQueryService.getUserExchangeHistoryDetail(PARTY_ID, FT_ID);

            assertThat(result.historyId()).isEqualTo(FT_ID);
            assertThat(result.amount()).isEqualByComparingTo("50000");
            assertThat(result.transferStatus()).isEqualTo("SUCCESS");
            assertThat(result.transferType()).isEqualTo("EXCHANGE");
            assertThat(result.accountNumber()).isEqualTo("1002-123-456789");
            assertThat(result.bankName()).isEqualTo("우리은행");
            assertThat(result.walletAddress()).isEqualTo("0xWallet");
            assertThat(result.txHash()).isEqualTo("0xdef");
            assertThat(result.blockchainStatus()).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("정상: blockchain_tx 없음 -> txHash/blockchainStatus = null")
        void success_withoutBlockchainTx() {
            when(fundTransferRepository.findByIdWithAccountAndWallet(FT_ID))
                    .thenReturn(Optional.of(fundTransfer(TransferType.EXCHANGE, PARTY_ID)));
            when(blockchainTxRepository.findByReferenceTypeAndReferenceId(
                            ReferenceType.FUND_TRANSFER, FT_ID))
                    .thenReturn(Optional.empty());

            UserExchangeHistoryDetail result =
                    fundTransferQueryService.getUserExchangeHistoryDetail(PARTY_ID, FT_ID);

            assertThat(result.txHash()).isNull();
            assertThat(result.blockchainStatus()).isNull();
        }

        @Test
        @DisplayName("fundTransfer 없음 -> HISTORY_NOT_FOUND")
        void throws_whenNotFound() {
            when(fundTransferRepository.findByIdWithAccountAndWallet(FT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    fundTransferQueryService.getUserExchangeHistoryDetail(
                                            PARTY_ID, FT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", UserErrorCode.HISTORY_NOT_FOUND);
        }

        @Test
        @DisplayName("타입 불일치(CHARGE를 EXCHANGE로 조회) -> HISTORY_NOT_FOUND")
        void throws_whenTypeMismatch() {
            when(fundTransferRepository.findByIdWithAccountAndWallet(FT_ID))
                    .thenReturn(Optional.of(fundTransfer(TransferType.CHARGE, PARTY_ID)));

            assertThatThrownBy(
                            () ->
                                    fundTransferQueryService.getUserExchangeHistoryDetail(
                                            PARTY_ID, FT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", UserErrorCode.HISTORY_NOT_FOUND);
        }

        @Test
        @DisplayName("소유자 불일치 -> NOT_OWNER")
        void throws_whenNotOwner() {
            Long otherPartyId = 999L;
            when(fundTransferRepository.findByIdWithAccountAndWallet(FT_ID))
                    .thenReturn(Optional.of(fundTransfer(TransferType.EXCHANGE, otherPartyId)));

            assertThatThrownBy(
                            () ->
                                    fundTransferQueryService.getUserExchangeHistoryDetail(
                                            PARTY_ID, FT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", UserErrorCode.NOT_OWNER);
        }
    }
}
