package family.fisa.hangangpay.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.ExchangeRequest;
import family.fisa.hangangpay.client.bank.dto.ExchangeResponse;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeRequestHashGenerator;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class ExchangeCommandServiceTest {

    @Mock ExchangeQueryService exchangeQueryService;
    @Mock ExchangeStateWriter stateWriter;
    @Mock BankClient bankClient;
    @Mock UserRepository userRepository;
    @Mock MerchantRepository merchantRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ExchangeIdempotencyStore idempotencyStore;
    @Mock ExchangeRequestHashGenerator requestHashGenerator;

    @InjectMocks ExchangeCommandService exchangeCommandService;

    private static final Long PARTY_ID = 10L;
    private static final Long TRANSACTION_ID = 100L;
    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TX_HASH = "0xabc123";
    private static final Long BANK_TX_ID = 999L;
    private static final String BANK_TX_ID_STR = "999";
    private static final String PIN = "123456";

    private ExchangeExecuteRequest request(String amount) {
        return new ExchangeExecuteRequest(UUID, new BigDecimal(amount), PIN);
    }

    /** stateWriter가 mock이라 필드는 거의 무의미. 오케스트레이터가 들고 다닐 비저장 엔티티 역할만 한다. */
    private Transaction exchangeTransaction() {
        return Transaction.builder().transactionUuid(UUID).build();
    }

    /** 성공 snapshot (RETURN_SNAPSHOT / 정상 완료 응답 공용) */
    private ExchangeExecuteResponse successResponse() {
        return ExchangeExecuteResponse.builder()
                .transactionId(TRANSACTION_ID)
                .transactionUuid(UUID)
                .amount(new BigDecimal("50000"))
                .accountNumber("110-1234-5678")
                .bankName("우리은행")
                .txHash(TX_HASH)
                .status(TransactionStatus.SUCCESS)
                .exchangedAt(LocalDateTime.now())
                .build();
    }

    /** "확인 중"(UNKNOWN) 응답 */
    private ExchangeExecuteResponse unknownResponse() {
        return ExchangeExecuteResponse.builder()
                .transactionId(TRANSACTION_ID)
                .transactionUuid(UUID)
                .amount(new BigDecimal("50000"))
                .accountNumber("110-1234-5678")
                .bankName("우리은행")
                .txHash(null)
                .status(TransactionStatus.UNKNOWN)
                .exchangedAt(LocalDateTime.now())
                .build();
    }

    private ExchangeResponse bankResponse() {
        return new ExchangeResponse(
                UUID, BANK_TX_ID, TX_HASH, 12345L, LocalDateTime.now(), new BigDecimal("50000"));
    }

    // ── 공통 stub ──────────────────────────────────────────────────────────

    private void stubUserPinPass() {
        User user = mock(User.class);
        when(userRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(user));
        when(user.matchesPaymentPin(anyString(), eq(passwordEncoder))).thenReturn(true);
    }

    private void stubMerchantPinPass() {
        Merchant merchant = mock(Merchant.class);
        when(merchantRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(merchant));
        when(merchant.matchesPaymentPin(anyString(), eq(passwordEncoder))).thenReturn(true);
    }

    private void stubGate(ExchangeIdempotencyDecision decision) {
        when(idempotencyStore.beginExecution(eq(UUID), any())).thenReturn(decision);
    }

    /** 사용자 정상 흐름: 게이트 NEW + 자격 통과 + claim → bank → complete stub */
    private ExchangeExecuteResponse stubUserHappyPath() {
        ExchangeRequest bankReq =
                new ExchangeRequest(UUID, 1L, "0xabc", "110-1234", new BigDecimal("50000"));
        ExchangeExecuteResponse expected = successResponse();

        stubGate(ExchangeIdempotencyDecision.newRequest());
        when(exchangeQueryService.checkEligibility(PARTY_ID)).thenReturn(true);
        when(stateWriter.claimExchange(eq(PARTY_ID), any(ExchangeExecuteRequest.class)))
                .thenReturn(exchangeTransaction());
        when(stateWriter.buildBankRequest(
                        any(Transaction.class), any(ExchangeExecuteRequest.class)))
                .thenReturn(bankReq);
        when(bankClient.exchange(bankReq)).thenReturn(bankResponse());
        when(stateWriter.completeExchange(any(Transaction.class), eq(TX_HASH), eq(BANK_TX_ID_STR)))
                .thenReturn(expected);
        return expected;
    }

    // ── 멱등 게이트 분기 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("멱등 게이트")
    class Idempotency {

        @Test
        @DisplayName("RETURN_SNAPSHOT -> 저장된 응답 반환, claim/bank 호출 안 함")
        void 멱등_성공_재요청() {
            stubUserPinPass();
            ExchangeExecuteResponse snapshot = successResponse();
            stubGate(ExchangeIdempotencyDecision.returnSnapshot(snapshot));

            ExchangeExecuteResponse response =
                    exchangeCommandService.executeUserExchange(PARTY_ID, request("50000"));

            assertThat(response).isSameAs(snapshot);
            verify(exchangeQueryService, never()).checkEligibility(any());
            verify(stateWriter, never()).claimExchange(any(), any());
            verify(bankClient, never()).exchange(any());
        }

        @Test
        @DisplayName("ALREADY_FAILED -> EXCHANGE_ALREADY_FAILED")
        void 멱등_이미_실패() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.alreadyFailed());

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_ALREADY_FAILED);

            verify(stateWriter, never()).claimExchange(any(), any());
        }

        @Test
        @DisplayName("PROCESSING -> EXCHANGE_IN_PROGRESS")
        void 멱등_진행중() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.processing());

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_IN_PROGRESS);

            verify(stateWriter, never()).claimExchange(any(), any());
        }

        @Test
        @DisplayName("CONFLICT -> IDEMPOTENCY_CONFLICT")
        void 멱등_충돌() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.conflict());

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.IDEMPOTENCY_CONFLICT);

            verify(stateWriter, never()).claimExchange(any(), any());
        }
    }

    // ── 환전 자격 ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("환전 자격 (60% 룰)")
    class Eligibility {

        @Test
        @DisplayName("자격 미달 -> EXCHANGE_NOT_ELIGIBLE, claim 호출 안 함")
        void 자격_미달() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.newRequest());
            when(exchangeQueryService.checkEligibility(PARTY_ID)).thenReturn(false);

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_NOT_ELIGIBLE);

            verify(stateWriter, never()).claimExchange(any(), any());
        }

        @Test
        @DisplayName("자격 통과 -> 정상 실행")
        void 자격_통과() {
            stubUserPinPass();
            ExchangeExecuteResponse expected = stubUserHappyPath();

            ExchangeExecuteResponse response =
                    exchangeCommandService.executeUserExchange(PARTY_ID, request("50000"));

            assertThat(response).isSameAs(expected);
        }
    }

    // ── 정상 흐름 ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("정상 흐름")
    class HappyPath {

        @Test
        @DisplayName("게이트 NEW + 자격 통과 -> claim -> bank -> complete -> 멱등snapshot 저장")
        void 정상_호출_순서() {
            stubUserPinPass();
            ExchangeExecuteResponse expected = stubUserHappyPath();

            ExchangeExecuteResponse response =
                    exchangeCommandService.executeUserExchange(PARTY_ID, request("50000"));

            assertThat(response).isSameAs(expected);
            verify(stateWriter).claimExchange(eq(PARTY_ID), any(ExchangeExecuteRequest.class));
            verify(bankClient).exchange(any(ExchangeRequest.class));
            verify(stateWriter)
                    .completeExchange(any(Transaction.class), eq(TX_HASH), eq(BANK_TX_ID_STR));
            verify(idempotencyStore).completeExecution(UUID, expected);
            verify(stateWriter, never()).markUnknownExchange(any(Transaction.class));
            verify(idempotencyStore, never()).failExecution(any());
        }
    }

    // ── 응답 불확실(타임아웃) → UNKNOWN ──────────────────────────────────────

    @Nested
    @DisplayName("응답 불확실 → UNKNOWN")
    class UnknownHandling {

        @Test
        @DisplayName("ResourceAccessException -> markUnknownExchange 반환, 재던짐/failExecution 안 함")
        void 타임아웃_UNKNOWN() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.newRequest());
            when(exchangeQueryService.checkEligibility(PARTY_ID)).thenReturn(true);

            ExchangeRequest bankReq =
                    new ExchangeRequest(UUID, 1L, "0xabc", "110-1234", new BigDecimal("50000"));
            when(stateWriter.claimExchange(eq(PARTY_ID), any(ExchangeExecuteRequest.class)))
                    .thenReturn(exchangeTransaction());
            when(stateWriter.buildBankRequest(
                            any(Transaction.class), any(ExchangeExecuteRequest.class)))
                    .thenReturn(bankReq);
            when(bankClient.exchange(bankReq)).thenThrow(new ResourceAccessException("timeout"));
            ExchangeExecuteResponse unknown = unknownResponse();
            when(stateWriter.markUnknownExchange(any(Transaction.class))).thenReturn(unknown);

            ExchangeExecuteResponse response =
                    exchangeCommandService.executeUserExchange(PARTY_ID, request("50000"));

            assertThat(response).isSameAs(unknown);
            verify(stateWriter).markUnknownExchange(any(Transaction.class));
            verify(idempotencyStore, never()).failExecution(any());
            verify(idempotencyStore, never()).completeExecution(any(), any());
            verify(stateWriter, never()).completeExchange(any(Transaction.class), any(), any());
        }
    }

    // ── 실패 처리 ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("실패 처리")
    class FailureHandling {

        @Test
        @DisplayName("bank 명확 거절(RuntimeException) -> 멱등 failExecution + 원 예외 재던짐, 저장 안 함")
        void bank_거절() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.newRequest());
            when(exchangeQueryService.checkEligibility(PARTY_ID)).thenReturn(true);

            ExchangeRequest bankReq =
                    new ExchangeRequest(UUID, 1L, "0xabc", "110-1234", new BigDecimal("50000"));
            when(stateWriter.claimExchange(eq(PARTY_ID), any(ExchangeExecuteRequest.class)))
                    .thenReturn(exchangeTransaction());
            when(stateWriter.buildBankRequest(
                            any(Transaction.class), any(ExchangeExecuteRequest.class)))
                    .thenReturn(bankReq);
            RuntimeException bankEx = new RuntimeException("bank rejected");
            when(bankClient.exchange(bankReq)).thenThrow(bankEx);

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isSameAs(bankEx);

            verify(idempotencyStore).failExecution(UUID);
            verify(stateWriter, never()).markUnknownExchange(any(Transaction.class));
            verify(stateWriter, never()).completeExchange(any(Transaction.class), any(), any());
            verify(idempotencyStore, never()).completeExecution(any(), any());
        }

        @Test
        @DisplayName("claim 단계 예외 -> bank/complete/markUnknown/failExecution 미호출")
        void claim_실패() {
            stubUserPinPass();
            stubGate(ExchangeIdempotencyDecision.newRequest());
            when(exchangeQueryService.checkEligibility(PARTY_ID)).thenReturn(true);

            BusinessException claimEx =
                    new BusinessException(TransactionErrorCode.WALLET_NOT_FOUND);
            when(stateWriter.claimExchange(eq(PARTY_ID), any(ExchangeExecuteRequest.class)))
                    .thenThrow(claimEx);

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isSameAs(claimEx);

            verify(bankClient, never()).exchange(any());
            verify(stateWriter, never()).completeExchange(any(Transaction.class), any(), any());
            verify(stateWriter, never()).markUnknownExchange(any(Transaction.class));
            verify(idempotencyStore, never()).failExecution(any());
        }
    }

    // ── 가맹점 환전 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("가맹점 환전 (executeMerchantExchange)")
    class MerchantExchange {

        private ExchangeExecuteResponse stubMerchantHappyPath() {
            ExchangeRequest bankReq =
                    new ExchangeRequest(UUID, 1L, "0xabc", "110-1234", new BigDecimal("50000"));
            ExchangeExecuteResponse expected = successResponse();

            stubGate(ExchangeIdempotencyDecision.newRequest());
            when(stateWriter.claimSettlementExchange(
                            eq(PARTY_ID), any(ExchangeExecuteRequest.class)))
                    .thenReturn(exchangeTransaction());
            when(stateWriter.buildBankRequest(
                            any(Transaction.class), any(ExchangeExecuteRequest.class)))
                    .thenReturn(bankReq);
            when(bankClient.exchange(bankReq)).thenReturn(bankResponse());
            when(stateWriter.completeExchange(
                            any(Transaction.class), eq(TX_HASH), eq(BANK_TX_ID_STR)))
                    .thenReturn(expected);
            return expected;
        }

        @Test
        @DisplayName("정상: 자격 검증 없이 claimSettlementExchange -> bank -> complete")
        void 정상_흐름() {
            stubMerchantPinPass();
            ExchangeExecuteResponse expected = stubMerchantHappyPath();

            ExchangeExecuteResponse response =
                    exchangeCommandService.executeMerchantExchange(PARTY_ID, request("50000"));

            assertThat(response).isSameAs(expected);
            verify(stateWriter)
                    .claimSettlementExchange(eq(PARTY_ID), any(ExchangeExecuteRequest.class));
            verify(stateWriter, never()).claimExchange(any(), any());
            verify(exchangeQueryService, never()).checkEligibility(any());
            verify(bankClient).exchange(any(ExchangeRequest.class));
            verify(idempotencyStore).completeExecution(UUID, expected);
        }

        @Test
        @DisplayName("RETURN_SNAPSHOT -> claim/bank 없이 기존 결과 반환")
        void 멱등_성공_재요청() {
            stubMerchantPinPass();
            ExchangeExecuteResponse snapshot = successResponse();
            stubGate(ExchangeIdempotencyDecision.returnSnapshot(snapshot));

            ExchangeExecuteResponse response =
                    exchangeCommandService.executeMerchantExchange(PARTY_ID, request("50000"));

            assertThat(response).isSameAs(snapshot);
            verify(stateWriter, never()).claimSettlementExchange(any(), any());
            verify(bankClient, never()).exchange(any());
        }

        @Test
        @DisplayName("ALREADY_FAILED -> EXCHANGE_ALREADY_FAILED")
        void 멱등_이미_실패() {
            stubMerchantPinPass();
            stubGate(ExchangeIdempotencyDecision.alreadyFailed());

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeMerchantExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_ALREADY_FAILED);

            verify(stateWriter, never()).claimSettlementExchange(any(), any());
        }

        @Test
        @DisplayName("bank 거절 -> failExecution + 원 예외 재던짐")
        void bank_거절() {
            stubMerchantPinPass();
            stubGate(ExchangeIdempotencyDecision.newRequest());

            ExchangeRequest bankReq =
                    new ExchangeRequest(UUID, 1L, "0xabc", "110-1234", new BigDecimal("50000"));
            when(stateWriter.claimSettlementExchange(
                            eq(PARTY_ID), any(ExchangeExecuteRequest.class)))
                    .thenReturn(exchangeTransaction());
            when(stateWriter.buildBankRequest(
                            any(Transaction.class), any(ExchangeExecuteRequest.class)))
                    .thenReturn(bankReq);
            RuntimeException bankEx = new RuntimeException("bank rejected");
            when(bankClient.exchange(bankReq)).thenThrow(bankEx);

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeMerchantExchange(
                                            PARTY_ID, request("50000")))
                    .isSameAs(bankEx);

            verify(idempotencyStore).failExecution(UUID);
            verify(stateWriter, never()).completeExchange(any(Transaction.class), any(), any());
        }
    }

    // ── PIN 검증 (게이트보다 먼저) ─────────────────────────────────────────

    @Nested
    @DisplayName("PIN 검증")
    class PinVerification {

        @Test
        @DisplayName("user PIN 불일치 -> INVALID_PAYMENT_PIN, 게이트 미진입")
        void user_pin_불일치() {
            User user = mock(User.class);
            when(userRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(user));
            when(user.matchesPaymentPin(anyString(), eq(passwordEncoder))).thenReturn(false);

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.INVALID_PAYMENT_PIN);

            verify(idempotencyStore, never()).beginExecution(any(), any());
            verify(stateWriter, never()).claimExchange(any(), any());
        }

        @Test
        @DisplayName("merchant PIN 불일치 -> INVALID_PAYMENT_PIN, 게이트 미진입")
        void merchant_pin_불일치() {
            Merchant merchant = mock(Merchant.class);
            when(merchantRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(merchant));
            when(merchant.matchesPaymentPin(anyString(), eq(passwordEncoder))).thenReturn(false);

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeMerchantExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.INVALID_PAYMENT_PIN);

            verify(idempotencyStore, never()).beginExecution(any(), any());
            verify(stateWriter, never()).claimSettlementExchange(any(), any());
        }

        @Test
        @DisplayName("user 없음 -> USER_NOT_FOUND")
        void user_없음() {
            when(userRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("merchant 없음 -> MERCHANT_NOT_FOUND")
        void merchant_없음() {
            when(merchantRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeMerchantExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(MerchantErrorCode.MERCHANT_NOT_FOUND);
        }
    }
}
