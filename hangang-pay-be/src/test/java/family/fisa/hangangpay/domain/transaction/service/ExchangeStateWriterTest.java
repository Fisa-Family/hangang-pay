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
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeStateWriter;
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
        return new ExchangeExecuteRequest(UUID, new BigDecimal("50000"), "123456");
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

    /** 저장된 상태를 가정한 EXCHANGE 거래 (reconcile/complete 계열 테스트용) */
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
    @DisplayName("claimExchange (미저장 엔티티 생성)")
    class ClaimExchange {

        @Test
        @DisplayName("정상: wallet/주거래계좌 조회 후 미저장 PENDING 엔티티 반환 (save 호출 안 함)")
        void 정상_생성() {
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(wallet()));
            when(accountRepository.findByParty_IdAndAccountType(PARTY_ID, AccountType.PRIMARY))
                    .thenReturn(Optional.of(primaryAccount()));

            Transaction result = stateWriter.claimExchange(PARTY_ID, request());

            assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
            assertThat(result.getTransactionType()).isEqualTo(TransactionType.EXCHANGE);
            assertThat(result.getTransactionUuid()).isEqualTo(UUID);
            assertThat(result.getToAccount().getAccountType()).isEqualTo(AccountType.PRIMARY);
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("50000"));
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("wallet 없음 -> WALLET_NOT_FOUND")
        void wallet_없음() {
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stateWriter.claimExchange(PARTY_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.WALLET_NOT_FOUND);

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("주거래 계좌 없음 -> ACCOUNT_NOT_FOUND")
        void 주거래계좌_없음() {
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(wallet()));
            when(accountRepository.findByParty_IdAndAccountType(PARTY_ID, AccountType.PRIMARY))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> stateWriter.claimExchange(PARTY_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);

            verify(transactionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("buildBankRequest (엔티티 기반)")
    class BuildBankRequest {

        @Test
        @DisplayName("정상: 전달된 엔티티에서 institution/wallet/account 추출")
        void 정상_빌드() {
            Transaction tx = pendingExchange();

            ExchangeRequest result = stateWriter.buildBankRequest(tx, request());

            assertThat(result.transactionUuid()).isEqualTo(UUID);
            assertThat(result.institutionId()).isEqualTo(INSTITUTION_ID);
            assertThat(result.walletAddress()).isEqualTo(WALLET_ADDRESS);
            assertThat(result.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("50000"));
        }
    }

    @Nested
    @DisplayName("completeExchange")
    class CompleteExchange {

        @Test
        @DisplayName("[execute] 엔티티 기반: SUCCESS 전환 + save + 응답 반환")
        void execute_정상_완료() {
            Transaction tx = pendingExchange();
            when(transactionRepository.save(tx)).thenReturn(tx);

            ExchangeExecuteResponse response =
                    stateWriter.completeExchange(tx, TX_HASH, BANK_TX_ID_STR);

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
            assertThat(tx.getTxHash()).isEqualTo(TX_HASH);
            assertThat(tx.getBankTransactionId()).isEqualTo(BANK_TX_ID_STR);
            assertThat(response.transactionId()).isEqualTo(TRANSACTION_ID);
            assertThat(response.txHash()).isEqualTo(TX_HASH);
            verify(transactionRepository).save(tx);
        }

        @Test
        @DisplayName("[reconcile] id 기반: 조회 후 SUCCESS 전환 + 응답 반환")
        void reconcile_정상_완료() {
            Transaction tx = pendingExchange();
            when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(tx));

            ExchangeExecuteResponse response =
                    stateWriter.completeExchange(TRANSACTION_ID, TX_HASH, BANK_TX_ID_STR);

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
            assertThat(response.txHash()).isEqualTo(TX_HASH);
        }

        @Test
        @DisplayName("[reconcile] tx 없음 -> EXCHANGE_NOT_FOUND")
        void reconcile_tx_없음() {
            when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

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
    @DisplayName("markUnknownExchange (엔티티 기반)")
    class MarkUnknownExchange {

        @Test
        @DisplayName("정상: UNKNOWN 전환 + save + 응답 반환")
        void 정상_unknown() {
            Transaction tx = pendingExchange();
            when(transactionRepository.save(tx)).thenReturn(tx);

            ExchangeExecuteResponse response = stateWriter.markUnknownExchange(tx);

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.UNKNOWN);
            assertThat(response.status()).isEqualTo(TransactionStatus.UNKNOWN);
            verify(transactionRepository).save(tx);
        }
    }

    @Nested
    @DisplayName("failExchange (id 기반)")
    class FailExchange {

        @Test
        @DisplayName("정상: 조회 후 FAILED 전환")
        void 정상_실패_마킹() {
            Transaction tx = pendingExchange();
            when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(tx));

            stateWriter.failExchange(TRANSACTION_ID);

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        }

        @Test
        @DisplayName("tx 없음 -> 조용히 통과 (예외 없음)")
        void tx_없음_무시() {
            when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

            stateWriter.failExchange(TRANSACTION_ID);
            // 예외 안 던지면 통과
        }
    }

    @Nested
    @DisplayName("incrementReconcileAttempt")
    class IncrementReconcileAttempt {

        @Test
        @DisplayName("정상이면 count 1 증가 후 새 값 반환")
        void increment_정상() {
            Transaction tx =
                    Transaction.builder()
                            .id(TRANSACTION_ID)
                            .transactionUuid(UUID)
                            .transactionType(TransactionType.EXCHANGE)
                            .status(TransactionStatus.UNKNOWN)
                            .reconcileAttemptCount(3)
                            .build();
            when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(tx));

            int newCount = stateWriter.incrementReconcileAttempt(TRANSACTION_ID);

            assertThat(newCount).isEqualTo(4);
            assertThat(tx.getReconcileAttemptCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("존재하지 않는 transactionId면 EXCHANGE_NOT_FOUND")
        void increment_없는_트랜잭션() {
            when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stateWriter.incrementReconcileAttempt(TRANSACTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(TransactionErrorCode.EXCHANGE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("claimSettlementExchange (미저장 엔티티 생성)")
    class ClaimSettlementExchange {

        private Account settlementAccount() {
            return Account.builder()
                    .id(2L)
                    .party(party())
                    .institution(institution())
                    .accountType(AccountType.SETTLEMENT)
                    .accountNumber(ACCOUNT_NUMBER)
                    .build();
        }

        @Test
        @DisplayName("정상: SETTLEMENT 계좌로 미저장 PENDING 엔티티 반환 (save 호출 안 함)")
        void 정상_생성() {
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(wallet()));
            when(accountRepository.findByParty_IdAndAccountType(PARTY_ID, AccountType.SETTLEMENT))
                    .thenReturn(Optional.of(settlementAccount()));

            Transaction result = stateWriter.claimSettlementExchange(PARTY_ID, request());

            assertThat(result.getToAccount().getAccountType()).isEqualTo(AccountType.SETTLEMENT);
            assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
            verify(accountRepository)
                    .findByParty_IdAndAccountType(PARTY_ID, AccountType.SETTLEMENT);
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("SETTLEMENT 계좌 없음 -> ACCOUNT_NOT_FOUND")
        void 정산계좌_없음() {
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(wallet()));
            when(accountRepository.findByParty_IdAndAccountType(PARTY_ID, AccountType.SETTLEMENT))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> stateWriter.claimSettlementExchange(PARTY_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);

            verify(transactionRepository, never()).save(any());
        }
    }
}
