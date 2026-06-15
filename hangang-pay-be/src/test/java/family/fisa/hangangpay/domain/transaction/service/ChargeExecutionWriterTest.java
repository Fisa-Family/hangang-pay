package family.fisa.hangangpay.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.dto.request.ChargeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.charge.ChargeRequestHashGenerator;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeExecutionWriter;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
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

@ExtendWith(MockitoExtension.class)
class ChargeExecutionWriterTest {

    @Mock TransactionRepository transactionRepository;
    @Mock AccountRepository accountRepository;
    @Mock WalletRepository walletRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ChargeIdempotencyStore chargeIdempotencyStore;
    @Mock ChargeRequestHashGenerator chargeRequestHashGenerator;

    @InjectMocks ChargeExecutionWriter writer;

    private static final Long PARTY_ID = 10L;
    private static final Long ACCOUNT_ID = 1L;
    private static final Long INSTITUTION_ID = 1L;
    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String ACCOUNT_NUMBER = "110-1234-5678";
    private static final String BANK_NAME = "우리은행";

    private Party party() {
        return Party.builder().id(PARTY_ID).partyType(PartyType.USER).build();
    }

    private Institution institution() {
        return Institution.builder()
                .id(INSTITUTION_ID)
                .institutionCode("020")
                .institutionName(BANK_NAME)
                .build();
    }

    private Account account() {
        return Account.builder()
                .id(ACCOUNT_ID)
                .party(party())
                .institution(institution())
                .accountType(AccountType.PRIMARY)
                .accountNumber(ACCOUNT_NUMBER)
                .build();
    }

    private Wallet wallet() {
        return Wallet.builder()
                .id(1L)
                .party(party())
                .institution(institution())
                .address("0xabc123")
                .build();
    }

    private ChargeIntentCreateRequest request() {
        return new ChargeIntentCreateRequest(INSTITUTION_ID, ACCOUNT_ID, new BigDecimal("50000"));
    }

    private Transaction charge(TransactionStatus status) {
        return Transaction.builder()
                .id(100L)
                .transactionUuid(UUID)
                .transactionType(TransactionType.CHARGE)
                .status(status)
                .fromParty(party())
                .fromAccount(account())
                .toWallet(wallet())
                .amount(new BigDecimal("50000"))
                .discountAmount(new BigDecimal("5000"))
                .discountRate(new BigDecimal("0.1"))
                .build();
    }

    @Nested
    @DisplayName("createIntent")
    class CreateIntent {

        @Test
        @DisplayName("서버 발급 uuid로 PENDING insert 후 intent 응답(PENDING, finalAmount=충전가-할인)")
        void 신규_PENDING() {
            when(accountRepository.findByIdAndParty_Id(ACCOUNT_ID, PARTY_ID))
                    .thenReturn(Optional.of(account()));
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(wallet()));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ChargeIntentResponse response =
                    writer.createIntent(PARTY_ID, request(), LocalDateTime.now());

            assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);
            assertThat(response.transactionUuid()).isNotBlank();
            assertThat(response.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
            assertThat(response.bankName()).isEqualTo(BANK_NAME);
            assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("50000"));
            assertThat(response.finalAmount()).isEqualByComparingTo(new BigDecimal("45000"));
            verify(transactionRepository).save(any(Transaction.class));
        }

        @Test
        @DisplayName("두 번 호출하면 서로 다른 uuid가 발급된다")
        void 두_번_호출_다른_uuid() {
            when(accountRepository.findByIdAndParty_Id(ACCOUNT_ID, PARTY_ID))
                    .thenReturn(Optional.of(account()));
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(wallet()));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ChargeIntentResponse first =
                    writer.createIntent(PARTY_ID, request(), LocalDateTime.now());
            ChargeIntentResponse second =
                    writer.createIntent(PARTY_ID, request(), LocalDateTime.now());

            assertThat(first.transactionUuid()).isNotBlank();
            assertThat(first.transactionUuid()).isNotEqualTo(second.transactionUuid());
        }

        @Test
        @DisplayName("계좌 없음 -> ACCOUNT_NOT_FOUND")
        void 계좌_없음() {
            when(accountRepository.findByIdAndParty_Id(ACCOUNT_ID, PARTY_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> writer.createIntent(PARTY_ID, request(), LocalDateTime.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);

            verify(transactionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("markExpired")
    class MarkExpired {

        @Test
        @DisplayName("PENDING -> EXPIRED 전환")
        void 만료() {
            Transaction tx = charge(TransactionStatus.PENDING);
            when(transactionRepository.findByTransactionUuid(UUID)).thenReturn(Optional.of(tx));

            writer.markExpired(UUID);

            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.EXPIRED);
        }
    }
}
