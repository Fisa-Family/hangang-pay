package family.fisa.hangangpay.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.client.bank.dto.ExchangeRequest;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
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
class ExchangeStateWriterTest {

    @Mock TransactionRepository transactionRepository;
    @Mock WalletRepository walletRepository;
    @Mock AccountRepository accountRepository;

    @InjectMocks ExchangeStateWriter stateWriter;

    private static final Long PARTY_ID = 10L;
    private static final Long INSTITUTION_ID = 1L;
    private static final Long TRANSACTION_ID = 100L;
    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String WALLET_ADDRESS = "0xabc123";
    private static final String ACCOUNT_NUMBER = "110-1234-5678";
    private static final String TX_HASH = "0xdef456";
    private static final String BANK_TX_ID_STR = "999";

    private ExchangeExecuteRequest request() {
        return new ExchangeExecuteRequest(UUID, new BigDecimal("50000"));
    }

    private Party party() {
        return Party.builder().id(PARTY_ID).partyType(PartyType.USER).build();
    }

    private Institution institution() {
        return Institution.builder()
                .id(INSTITUTION_ID)
                .institutionCode("020")
                .institutionName("우리은행")
                .build();
    }

    private Wallet wallet() {
        return Wallet.builder()
                .id(1L)
                .party(party())
                .institution(institution())
                .address(WALLET_ADDRESS)
                .build();
    }

    private Account primaryAccount() {
        return Account.builder()
                .id(1L)
                .party(party())
                .institution(institution())
                .accountType(AccountType.PRIMARY)
                .accountNumber(ACCOUNT_NUMBER)
                .build();
    }

    private Transaction pendingExchange() {
        return Transaction.builder()
                .id(TRANSACTION_ID)
                .transactionUuid(UUID)
                .transactionType(TransactionType.EXCHANGE)
                .status(TransactionStatus.PENDING)
                .fromParty(party())
                .fromWallet(wallet())
                .toAccount(primaryAccount())
                .amount(new BigDecimal("50000"))
                .build();
    }

    @Nested
    @DisplayName("claimExchange")
    class ClaimExchange {

        @Test
        @DisplayName("정상: wallet 락 -> inflight 미존재 -> 주거래 계좌 -> PENDING 저장 후 id 반환")
        void 정상_저장() {
            // given
            when(walletRepository.findByParty_IdForUpdate(PARTY_ID))
                    .thenReturn(Optional.of(wallet()));
            when(transactionRepository.existsInflightExchange(PARTY_ID)).thenReturn(false);
            when(accountRepository.findByParty_IdAndAccountType(PARTY_ID, AccountType.PRIMARY))
                    .thenReturn(Optional.of(primaryAccount()));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(
                            invocation -> {
                                Transaction tx = invocation.getArgument(0);
                                ReflectionTestUtils.setField(tx, "id", TRANSACTION_ID);
                                return tx;
                            });

            // when
            Long id = stateWriter.claimExchange(PARTY_ID, request());

            // then
            assertThat(id).isEqualTo(TRANSACTION_ID);
            verify(transactionRepository).save(any(Transaction.class));
        }

        @Test
        @DisplayName("wallet 없음 -> WALLET_NOT_FOUND")
        void wallet_없음() {
            // given
            when(walletRepository.findByParty_IdForUpdate(PARTY_ID)).thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(() -> stateWriter.claimExchange(PARTY_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.WALLET_NOT_FOUND);

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("inflight EXCHANGE 존재 -> EXCHANGE_IN_PROGRESS")
        void inflight_존재() {
            // given
            when(walletRepository.findByParty_IdForUpdate(PARTY_ID))
                    .thenReturn(Optional.of(wallet()));
            when(transactionRepository.existsInflightExchange(PARTY_ID)).thenReturn(true);

            // when, then
            assertThatThrownBy(() -> stateWriter.claimExchange(PARTY_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_IN_PROGRESS);

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("주거래 계좌 없음 -> ACCOUNT_NOT_FOUND")
        void 주거래계좌_없음() {
            // given
            when(walletRepository.findByParty_IdForUpdate(PARTY_ID))
                    .thenReturn(Optional.of(wallet()));
            when(transactionRepository.existsInflightExchange(PARTY_ID)).thenReturn(false);
            when(accountRepository.findByParty_IdAndAccountType(PARTY_ID, AccountType.PRIMARY))
                    .thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(() -> stateWriter.claimExchange(PARTY_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);

            verify(transactionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("buildBankRequest")
    class BuildBankRequest {

        @Test
        @DisplayName("정상: tx에서 institution/wallet/account 추출")
        void 정상_빌드() {
            // given
            when(transactionRepository.findById(TRANSACTION_ID))
                    .thenReturn(Optional.of(pendingExchange()));

            // when
            ExchangeRequest result = stateWriter.buildBankRequest(TRANSACTION_ID, request());

            // then
            assertThat(result.transactionUuid()).isEqualTo(UUID);
            assertThat(result.institutionId()).isEqualTo(INSTITUTION_ID);
            assertThat(result.walletAddress()).isEqualTo(WALLET_ADDRESS);
            assertThat(result.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("50000"));
        }

        @Test
        @DisplayName("tx 없음 -> EXCHANGE_NOT_FOUND")
        void tx_없음() {
            // given
            when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(() -> stateWriter.buildBankRequest(TRANSACTION_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("completeExchange")
    class CompleteExchange {

        @Test
        @DisplayName("정상: PENDING -> SUCCESS 전환 + txHash/bankTransactionId 저장 + 응답 반환")
        void 정상_완료() {
            // given
            Transaction tx = pendingExchange();
            when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(tx));

            // when
            ExchangeExecuteResponse response =
                    stateWriter.completeExchange(TRANSACTION_ID, TX_HASH, BANK_TX_ID_STR);

            // then
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
            assertThat(tx.getTxHash()).isEqualTo(TX_HASH);
            assertThat(tx.getBankTransactionId()).isEqualTo(BANK_TX_ID_STR);
            assertThat(response.transactionId()).isEqualTo(TRANSACTION_ID);
            assertThat(response.txHash()).isEqualTo(TX_HASH);
        }

        @Test
        @DisplayName("tx 없음 -> EXCHANGE_NOT_FOUND")
        void tx_없음() {
            // given
            when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(
                            () ->
                                    stateWriter.completeExchange(
                                            TRANSACTION_ID, TX_HASH, BANK_TX_ID_STR))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("failExchange")
    class FailExchange {

        @Test
        @DisplayName("정상: PENDING -> FAILED 전환")
        void 정상_실패_마킹() {
            // given
            Transaction tx = pendingExchange();
            when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(tx));

            // when
            stateWriter.failExchange(TRANSACTION_ID);

            // then
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        }

        @Test
        @DisplayName("tx 없음 -> 조용히 통과 (예외 없음)")
        void tx_없음_무시() {
            // given
            when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

            // when (예외 발생 없음)
            stateWriter.failExchange(TRANSACTION_ID);

            // then - 별도 검증 없음. 예외 안 던지면 통과
        }
    }

    @Nested
    @DisplayName("incrementReconcileAttempt")
    class IncrementReconcileAttempt {

        @Test
        @DisplayName("정상이면 count 1 증가 후 새 값 반환")
        void increment_정상() {
            // given
            Transaction tx =
                    Transaction.builder()
                            .id(TRANSACTION_ID)
                            .transactionUuid(UUID)
                            .transactionType(TransactionType.EXCHANGE)
                            .status(TransactionStatus.PENDING)
                            .reconcileAttemptCount(3)
                            .build();
            when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(tx));

            // when
            int newCount = stateWriter.incrementReconcileAttempt(TRANSACTION_ID);

            // then
            assertThat(newCount).isEqualTo(4);
            assertThat(tx.getReconcileAttemptCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("존재하지 않는 transactionId면 EXCHANGE_NOT_FOUND")
        void increment_없는_트랜잭션() {
            // given
            when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(() -> stateWriter.incrementReconcileAttempt(TRANSACTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_NOT_FOUND);
        }
    }
}
