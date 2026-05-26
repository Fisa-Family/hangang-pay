package family.fisa.hangangpay.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.ExchangeRequest;
import family.fisa.hangangpay.client.bank.dto.ExchangeResponse;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.ReconcileResult;
import family.fisa.hangangpay.domain.transaction.dto.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExchangeCommandServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock ExchangeStateWriter stateWriter;
    @Mock BankClient bankClient;
    @Mock ExchangeReconcileService exchangeReconcileService;

    @InjectMocks ExchangeCommandService exchangeCommandService;

    private static final Long PARTY_ID = 10L;
    private static final Long TRANSACTION_ID = 100L;
    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TX_HASH = "0xabc123";
    private static final Long BANK_TX_ID = 999L;
    private static final String BANK_TX_ID_STR = "999";
    private static final LocalDateTime CHARGE_AT = LocalDateTime.of(2026, 5, 1, 0, 0);

    private ExchangeExecuteRequest request(String amount) {
        return new ExchangeExecuteRequest(UUID, new BigDecimal(amount));
    }

    private Party party() {
        return Party.builder().id(PARTY_ID).partyType(PartyType.USER).build();
    }

    private Institution institution() {
        return Institution.builder().id(1L).institutionCode("020").institutionName("우리은행").build();
    }

    private Account primaryAccount() {
        return Account.builder()
                .id(1L)
                .party(party())
                .institution(institution())
                .accountType(AccountType.PRIMARY)
                .accountNumber("110-1234-5678")
                .build();
    }

    private Transaction successTransaction() {
        Transaction tx =
                Transaction.builder()
                        .id(TRANSACTION_ID)
                        .transactionUuid(UUID)
                        .transactionType(TransactionType.EXCHANGE)
                        .status(TransactionStatus.SUCCESS)
                        .fromParty(party())
                        .toAccount(primaryAccount())
                        .amount(new BigDecimal("50000"))
                        .txHash(TX_HASH)
                        .build();
        ReflectionTestUtils.setField(tx, "updatedAt", LocalDateTime.now());
        return tx;
    }

    private Transaction transactionWithStatus(TransactionStatus status) {
        return Transaction.builder()
                .id(TRANSACTION_ID)
                .transactionUuid(UUID)
                .status(status)
                .build();
    }

    /** PENDING 상태 + 지정 createdAt 트랜잭션 (5분 임계 분기 시나리오용) */
    private Transaction pendingTransactionAt(LocalDateTime createdAt) {
        Transaction tx =
                Transaction.builder()
                        .id(TRANSACTION_ID)
                        .transactionUuid(UUID)
                        .transactionType(TransactionType.EXCHANGE)
                        .status(TransactionStatus.PENDING)
                        .build();
        ReflectionTestUtils.setField(tx, "createdAt", createdAt);
        return tx;
    }

    private Transaction latestCharge(String amount) {
        Transaction tx =
                Transaction.builder()
                        .transactionType(TransactionType.CHARGE)
                        .status(TransactionStatus.SUCCESS)
                        .amount(new BigDecimal(amount))
                        .build();
        ReflectionTestUtils.setField(tx, "createdAt", CHARGE_AT);
        return tx;
    }

    /** 자격 검증 통과 stub. balanceBefore = chargedBefore (PAYMENT/EXCHANGE 이전 합은 0) */
    private void stubEligibility(String chargedBefore, String chargeAmount, String usedSince) {
        when(transactionRepository.findByTransactionUuid(UUID)).thenReturn(Optional.empty());
        when(transactionRepository.findLatestSuccessCharge(PARTY_ID))
                .thenReturn(Optional.of(latestCharge(chargeAmount)));
        when(transactionRepository.sumSuccessByTypeBefore(
                        PARTY_ID, TransactionType.CHARGE, CHARGE_AT))
                .thenReturn(new BigDecimal(chargedBefore));
        when(transactionRepository.sumSuccessByTypeBefore(
                        PARTY_ID, TransactionType.PAYMENT, CHARGE_AT))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumSuccessByTypeBefore(
                        PARTY_ID, TransactionType.EXCHANGE, CHARGE_AT))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumSuccessByTypeSince(
                        PARTY_ID, TransactionType.PAYMENT, CHARGE_AT))
                .thenReturn(new BigDecimal(usedSince));
    }

    /** claim → bank → complete 정상 흐름 stub */
    private ExchangeExecuteResponse stubHappyPath() {
        ExchangeRequest bankReq =
                new ExchangeRequest(UUID, 1L, "0xabc", "110-1234", new BigDecimal("50000"));
        ExchangeResponse bankResp =
                new ExchangeResponse(
                        UUID,
                        BANK_TX_ID,
                        TX_HASH,
                        12345L,
                        LocalDateTime.now(),
                        new BigDecimal("50000"));
        ExchangeExecuteResponse expected =
                ExchangeExecuteResponse.builder().transactionId(TRANSACTION_ID).build();

        when(stateWriter.claimExchange(eq(PARTY_ID), any(ExchangeExecuteRequest.class)))
                .thenReturn(TRANSACTION_ID);
        when(stateWriter.buildBankRequest(eq(TRANSACTION_ID), any(ExchangeExecuteRequest.class)))
                .thenReturn(bankReq);
        when(bankClient.exchange(bankReq)).thenReturn(bankResp);
        when(stateWriter.completeExchange(TRANSACTION_ID, TX_HASH, BANK_TX_ID_STR))
                .thenReturn(expected);
        return expected;
    }

    @Nested
    @DisplayName("멱등성 정책")
    class Idempotency {

        @Test
        @DisplayName("같은 UUID로 SUCCESS 존재 -> 기존 결과 반환")
        void 멱등_SUCCESS_hit() {
            // given
            when(transactionRepository.findByTransactionUuid(UUID))
                    .thenReturn(Optional.of(successTransaction()));

            // when
            ExchangeExecuteResponse response =
                    exchangeCommandService.executeUserExchange(PARTY_ID, request("50000"));

            // then
            assertThat(response.transactionId()).isEqualTo(TRANSACTION_ID);
            assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
            verify(stateWriter, never()).claimExchange(any(), any());
            verify(bankClient, never()).exchange(any());
        }

        @Test
        @DisplayName("같은 UUID로 FAILED 존재 -> EXCHANGE_ALREADY_FAILED")
        void 멱등_FAILED_거절() {
            // given
            when(transactionRepository.findByTransactionUuid(UUID))
                    .thenReturn(Optional.of(transactionWithStatus(TransactionStatus.FAILED)));

            // when, then
            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_ALREADY_FAILED);

            verify(stateWriter, never()).claimExchange(any(), any());
        }
    }

    @Nested
    @DisplayName("환전 자격 (60% 룰)")
    class Eligibility {

        @Test
        @DisplayName("충전 이력 없음 -> EXCHANGE_NOT_ELIGIBLE")
        void 충전이력_없음() {
            // given
            when(transactionRepository.findByTransactionUuid(UUID)).thenReturn(Optional.empty());
            when(transactionRepository.findLatestSuccessCharge(PARTY_ID))
                    .thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_NOT_ELIGIBLE);
        }

        @Test
        @DisplayName("잔액 10K + 충전 60K = 70K, 사용 42K (정확히 60%) -> 통과")
        void 정확히_임계값_통과() {
            // given
            stubEligibility("10000", "60000", "42000");
            ExchangeExecuteResponse expected = stubHappyPath();

            // when
            ExchangeExecuteResponse response =
                    exchangeCommandService.executeUserExchange(PARTY_ID, request("50000"));

            // then
            assertThat(response).isSameAs(expected);
        }

        @Test
        @DisplayName("잔액 10K + 충전 60K = 70K, 사용 41,999 (1원 미달) -> 거절")
        void 임계값_미달() {
            // given
            stubEligibility("10000", "60000", "41999");

            // when, then
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
        @DisplayName("첫 충전 (잔액 0) + 충전 50K, 사용 30K -> 통과")
        void 첫_충전_통과() {
            // given
            stubEligibility("0", "50000", "30000");
            ExchangeExecuteResponse expected = stubHappyPath();

            // when
            ExchangeExecuteResponse response =
                    exchangeCommandService.executeUserExchange(PARTY_ID, request("50000"));

            // then
            assertThat(response).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("PENDING reconcile 분기")
    class PendingReconcile {

        @Test
        @DisplayName("PENDING 5분 이내 -> EXCHANGE_IN_PROGRESS (reconcile 호출 안 됨)")
        void pending_in_flight() {
            // given
            Transaction pending = pendingTransactionAt(LocalDateTime.now().minusMinutes(1));
            when(transactionRepository.findByTransactionUuid(UUID))
                    .thenReturn(Optional.of(pending));

            // when, then
            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_IN_PROGRESS);

            verify(exchangeReconcileService, never()).reconcile(any());
            verify(stateWriter, never()).claimExchange(any(), any());
        }

        @Test
        @DisplayName("PENDING 5분 초과 + reconcile SUCCESS -> 재조회한 tx로 응답 반환")
        void pending_orphan_reconcile_success() {
            // given
            Transaction pending = pendingTransactionAt(LocalDateTime.now().minusMinutes(6));
            Transaction updated = successTransaction();
            // findByTransactionUuid 1회: PENDING, 2회: SUCCESS (reconcile 후 재조회)
            when(transactionRepository.findByTransactionUuid(UUID))
                    .thenReturn(Optional.of(pending), Optional.of(updated));
            when(exchangeReconcileService.reconcile(TRANSACTION_ID))
                    .thenReturn(ReconcileResult.RECONCILED_SUCCESS);

            // when
            ExchangeExecuteResponse response =
                    exchangeCommandService.executeUserExchange(PARTY_ID, request("50000"));

            // then
            assertThat(response.transactionId()).isEqualTo(TRANSACTION_ID);
            assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
            verify(exchangeReconcileService).reconcile(TRANSACTION_ID);
            verify(stateWriter, never()).claimExchange(any(), any());
            verify(bankClient, never()).exchange(any());
        }

        @Test
        @DisplayName("PENDING 5분 초과 + reconcile FAILED -> EXCHANGE_ALREADY_FAILED")
        void pending_orphan_reconcile_failed() {
            // given
            Transaction pending = pendingTransactionAt(LocalDateTime.now().minusMinutes(6));
            when(transactionRepository.findByTransactionUuid(UUID))
                    .thenReturn(Optional.of(pending));
            when(exchangeReconcileService.reconcile(TRANSACTION_ID))
                    .thenReturn(ReconcileResult.RECONCILED_FAILED);

            // when, then
            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_ALREADY_FAILED);

            verify(exchangeReconcileService).reconcile(TRANSACTION_ID);
            verify(stateWriter, never()).claimExchange(any(), any());
        }

        @Test
        @DisplayName("PENDING 5분 초과 + reconcile SKIPPED -> EXCHANGE_IN_PROGRESS")
        void pending_orphan_reconcile_skipped() {
            // given
            Transaction pending = pendingTransactionAt(LocalDateTime.now().minusMinutes(6));
            when(transactionRepository.findByTransactionUuid(UUID))
                    .thenReturn(Optional.of(pending));
            when(exchangeReconcileService.reconcile(TRANSACTION_ID))
                    .thenReturn(ReconcileResult.SKIPPED);

            // when, then
            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_IN_PROGRESS);
        }

        @Test
        @DisplayName("PENDING 5분 초과 + reconcile ERROR -> EXCHANGE_IN_PROGRESS")
        void pending_orphan_reconcile_error() {
            // given
            Transaction pending = pendingTransactionAt(LocalDateTime.now().minusMinutes(6));
            when(transactionRepository.findByTransactionUuid(UUID))
                    .thenReturn(Optional.of(pending));
            when(exchangeReconcileService.reconcile(TRANSACTION_ID))
                    .thenReturn(ReconcileResult.RECONCILE_ERROR);

            // when, then
            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_IN_PROGRESS);
        }
    }

    @Nested
    @DisplayName("정상 흐름")
    class HappyPath {

        @Test
        @DisplayName("[D1+D2] 멱등 miss + 자격 통과 -> claim -> bank -> complete 순서 호출")
        void 정상_호출_순서() {
            // given
            stubEligibility("10000", "60000", "50000");
            ExchangeExecuteResponse expected = stubHappyPath();

            // when
            ExchangeExecuteResponse response =
                    exchangeCommandService.executeUserExchange(PARTY_ID, request("50000"));

            // then
            assertThat(response).isSameAs(expected);
            verify(stateWriter).claimExchange(eq(PARTY_ID), any(ExchangeExecuteRequest.class));
            verify(bankClient).exchange(any(ExchangeRequest.class));
            verify(stateWriter).completeExchange(TRANSACTION_ID, TX_HASH, BANK_TX_ID_STR);
            verify(stateWriter, never()).failExchange(any());
        }
    }

    @Nested
    @DisplayName("실패 처리")
    class FailureHandling {

        @Test
        @DisplayName("bank 호출 실패 -> failExchange 호출 + 원 예외 재던짐")
        void bank_실패() {
            // given
            stubEligibility("10000", "60000", "50000");

            ExchangeRequest bankReq =
                    new ExchangeRequest(UUID, 1L, "0xabc", "110-1234", new BigDecimal("50000"));
            when(stateWriter.claimExchange(eq(PARTY_ID), any(ExchangeExecuteRequest.class)))
                    .thenReturn(TRANSACTION_ID);
            when(stateWriter.buildBankRequest(
                            eq(TRANSACTION_ID), any(ExchangeExecuteRequest.class)))
                    .thenReturn(bankReq);

            RuntimeException bankEx = new RuntimeException("bank down");
            when(bankClient.exchange(bankReq)).thenThrow(bankEx);

            // when, then
            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isSameAs(bankEx);

            verify(stateWriter).failExchange(TRANSACTION_ID);
            verify(stateWriter, never()).completeExchange(any(), any(), any());
        }

        @Test
        @DisplayName("claim 단계 예외 -> bank/complete/fail 모두 호출 안 됨")
        void claim_실패() {
            // given
            stubEligibility("10000", "60000", "50000");

            BusinessException claimEx =
                    new BusinessException(TransactionErrorCode.WALLET_NOT_FOUND);
            when(stateWriter.claimExchange(eq(PARTY_ID), any(ExchangeExecuteRequest.class)))
                    .thenThrow(claimEx);

            // when, then
            assertThatThrownBy(
                            () ->
                                    exchangeCommandService.executeUserExchange(
                                            PARTY_ID, request("50000")))
                    .isSameAs(claimEx);

            verify(bankClient, never()).exchange(any());
            verify(stateWriter, never()).completeExchange(any(), any(), any());
            verify(stateWriter, never()).failExchange(any());
        }
    }
}
