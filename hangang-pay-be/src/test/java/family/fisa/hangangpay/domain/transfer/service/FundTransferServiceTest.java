package family.fisa.hangangpay.domain.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.domain.transfer.dto.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transfer.dto.ExchangeHistoryItem;
import family.fisa.hangangpay.domain.transfer.repository.FundTransferRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.pagination.PaginationService;
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
class FundTransferServiceTest {

    @Mock UserRepository userRepository;
    @Mock FundTransferRepository fundTransferRepository;
    @Mock PaginationService paginationService;

    @InjectMocks FundTransferService fundTransferService;

    @Nested
    @DisplayName("충전 내역 조회 (getChargeHistories)")
    class ChargeHistories {

        @Test
        @DisplayName("사용자 없으면 USER_NOT_FOUND 예외")
        void throwsWhenUserNotFound() {
            // given
            when(userRepository.findPartyIdByUserId(1L)).thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(() -> fundTransferService.getChargeHistories(
                    1L, new CursorPageRequest(null, null)))
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
            when(fundTransferRepository.findChargeHistoriesByPartyId(
                    partyId, ScrollPosition.keyset(), Limit.of(20))).thenReturn(window);
            when(paginationService.toCursorPage(window)).thenReturn(expected);

            // when
            CursorPageResponse<ChargeHistoryItem> actual =
                    fundTransferService.getChargeHistories(userId, new CursorPageRequest(null, null));

            // then
            assertThat(actual).isSameAs(expected);
            // CursorPageRequest.cursorCreatedAt == null 이면 ScrollPosition.keyset() 로 들어간다
            verify(fundTransferRepository).findChargeHistoriesByPartyId(
                    partyId, ScrollPosition.keyset(), Limit.of(20));
        }

        @Test
        @DisplayName("커서가 존재하면 forward(createdAt, id) 위치로 조회")
        void forwardCursor() {
            // given
            Long userId = 1L, partyId = 1L, cursorId = 1L;
            LocalDateTime cursorAt = LocalDateTime.of(2026, 5, 15, 10, 0);
            ScrollPosition expectedPos = ScrollPosition.forward(
                    Map.of("createdAt", cursorAt, "id", cursorId));

            Window<ChargeHistoryItem> window = mock(Window.class);
            CursorPageResponse<ChargeHistoryItem> expected =
                    new CursorPageResponse<>(List.of(), null, null, false);

            when(userRepository.findPartyIdByUserId(userId)).thenReturn(Optional.of(partyId));
            when(fundTransferRepository.findChargeHistoriesByPartyId(
                    partyId, expectedPos, Limit.of(20))).thenReturn(window);
            when(paginationService.toCursorPage(window)).thenReturn(expected);

            // when
            CursorPageResponse<ChargeHistoryItem> actual =
                    fundTransferService.getChargeHistories(
                            userId, new CursorPageRequest(cursorAt, cursorId));

            // then
            assertThat(actual).isSameAs(expected);
            verify(fundTransferRepository).findChargeHistoriesByPartyId(
                    partyId, expectedPos, Limit.of(20));
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
            assertThatThrownBy(() -> fundTransferService.getExchangeHistories(
                    1L, new CursorPageRequest(null, null)))
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
            when(fundTransferRepository.findExchangeHistoriesByPartyId(
                    partyId, ScrollPosition.keyset(), Limit.of(20))).thenReturn(window);
            when(paginationService.toCursorPage(window)).thenReturn(expected);

            // when
            CursorPageResponse<ExchangeHistoryItem> actual =
                    fundTransferService.getExchangeHistories(userId, new CursorPageRequest(null, null));

            // then
            assertThat(actual).isSameAs(expected);
            // CursorPageRequest.cursorCreatedAt == null 이면 ScrollPosition.keyset() 로 들어간다
            verify(fundTransferRepository).findExchangeHistoriesByPartyId(
                    partyId, ScrollPosition.keyset(), Limit.of(20));
        }

        @Test
        @DisplayName("커서가 존재하면 forward(createdAt, id) 위치로 조회")
        void forwardCursor() {
            // given
            Long userId = 1L, partyId = 1L, cursorId = 1L;
            LocalDateTime cursorAt = LocalDateTime.of(2026, 5, 15, 10, 0);
            ScrollPosition expectedPos = ScrollPosition.forward(
                    Map.of("createdAt", cursorAt, "id", cursorId));

            Window<ExchangeHistoryItem> window = mock(Window.class);
            CursorPageResponse<ExchangeHistoryItem> expected =
                    new CursorPageResponse<>(List.of(), null, null, false);

            when(userRepository.findPartyIdByUserId(userId)).thenReturn(Optional.of(partyId));
            when(fundTransferRepository.findExchangeHistoriesByPartyId(
                    partyId, expectedPos, Limit.of(20))).thenReturn(window);
            when(paginationService.toCursorPage(window)).thenReturn(expected);

            // when
            CursorPageResponse<ExchangeHistoryItem> actual =
                    fundTransferService.getExchangeHistories(
                            userId, new CursorPageRequest(cursorAt, cursorId));

            // then
            assertThat(actual).isSameAs(expected);
            verify(fundTransferRepository).findExchangeHistoriesByPartyId(
                    partyId, expectedPos, Limit.of(20));
        }
    }
}
