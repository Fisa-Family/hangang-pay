package family.fisa.hangangpay.global.init;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.jpa.InstitutionJpaRepository;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.jpa.MerchantJpaRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.service.WalletCommandService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    private final PartyRepository partyRepository;
    private final UserRepository userRepository;
    private final MerchantJpaRepository merchantJpaRepository;
    private final InstitutionJpaRepository institutionJpaRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final WalletCommandService walletCommandService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (partyRepository.count() > 0) {
            log.info("[be-local-seed] skipped");
            return;
        }
        log.info("[be-local-seed] start");

        // BC팀 SQL과 동일한 id/code/name (BE는 institution 캐시 역할)
        Institution bok =
                institutionJpaRepository.save(
                        Institution.builder()
                                .id(1L)
                                .institutionCode("BoK")
                                .institutionName("Bank of Korea")
                                .build());
        Institution woori =
                institutionJpaRepository.save(
                        Institution.builder()
                                .id(2L)
                                .institutionCode("WR")
                                .institutionName("Woori Bank")
                                .build());
        Institution shinhan =
                institutionJpaRepository.save(
                        Institution.builder()
                                .id(3L)
                                .institutionCode("SH")
                                .institutionName("Shinhan Bank")
                                .build());
        institutionJpaRepository.save(
                Institution.builder()
                        .id(4L)
                        .institutionCode("HN")
                        .institutionName("Hana Bank")
                        .build());

        Party userParty = partyRepository.save(Party.of(PartyType.USER));
        userRepository.save(
                User.builder()
                        .party(userParty)
                        .username("테스트유저")
                        .passwordHash(passwordEncoder.encode("password"))
                        .paymentPinHash(passwordEncoder.encode("123456"))
                        .phoneNumber("01012345678")
                        .birthDate(LocalDate.of(1995, 1, 1))
                        .region("성동구")
                        .build());
        Account userAccount =
                accountRepository.save(
                        Account.builder()
                                .party(userParty)
                                .institution(woori)
                                .accountType(AccountType.PRIMARY)
                                .accountNumber("1002-123-456789")
                                .build());
        Wallet userWallet = walletCommandService.createWallet(userParty, bok);

        Party merchantParty = partyRepository.save(Party.of(PartyType.MERCHANT));
        merchantJpaRepository.save(
                Merchant.builder()
                        .party(merchantParty)
                        .username("테스트가맹점")
                        .passwordHash(passwordEncoder.encode("password"))
                        .paymentPinHash(passwordEncoder.encode("123456"))
                        .businessNumber("123-45-67890")
                        .merchantName("한강떡볶이")
                        .ownerName("홍길동")
                        .phoneNumber("0226001234")
                        .address("서울시 성동구 왕십리로 222")
                        .latitude(new BigDecimal("37.5635030"))
                        .longitude(new BigDecimal("127.0369670"))
                        .build());
        Account merchantAccount =
                accountRepository.save(
                        Account.builder()
                                .party(merchantParty)
                                .institution(shinhan)
                                .accountType(AccountType.SETTLEMENT)
                                .accountNumber("110-987-654321")
                                .build());
        Wallet merchantWallet = walletCommandService.createWallet(merchantParty, bok);

        seedHistoryTransactions(
                userParty, userAccount, userWallet, merchantParty, merchantAccount, merchantWallet);

        log.info("[be-local-seed] done");
    }

    private void seedHistoryTransactions(
            Party userParty,
            Account userAccount,
            Wallet userWallet,
            Party merchantParty,
            Account merchantAccount,
            Wallet merchantWallet) {
        Transaction charge1 =
                transactionRepository.save(
                        Transaction.forCharge(
                                UUID.randomUUID().toString(),
                                userParty,
                                userAccount,
                                userWallet,
                                new BigDecimal("100000"),
                                new BigDecimal("10000"),
                                new BigDecimal("10.00")));
        charge1.completeWithBankResponse("0xCHARGETX0001", "BANK-TX-001");

        Transaction charge2 =
                transactionRepository.save(
                        Transaction.forCharge(
                                UUID.randomUUID().toString(),
                                userParty,
                                userAccount,
                                userWallet,
                                new BigDecimal("50000"),
                                new BigDecimal("5000"),
                                new BigDecimal("10.00")));
        charge2.completeWithBankResponse("0xCHARGETX0002", "BANK-TX-002");

        Transaction payment1 =
                transactionRepository.save(
                        Transaction.forPayment(
                                UUID.randomUUID().toString(),
                                userParty,
                                merchantParty,
                                userWallet,
                                merchantWallet,
                                new BigDecimal("15000"),
                                "APV-2026-00000001",
                                "떡볶이 1인분"));
        payment1.completeWithBankResponse("0xPAYTX0001", null);

        Transaction payment2 =
                transactionRepository.save(
                        Transaction.forPayment(
                                UUID.randomUUID().toString(),
                                userParty,
                                merchantParty,
                                userWallet,
                                merchantWallet,
                                new BigDecimal("8000"),
                                "APV-2026-00000002",
                                "순대 1인분"));
        payment2.completeWithBankResponse("0xPAYTX0002", null);

        Transaction exchange =
                transactionRepository.save(
                        Transaction.forExchange(
                                UUID.randomUUID().toString(),
                                merchantParty,
                                merchantWallet,
                                merchantAccount,
                                new BigDecimal("20000"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO));
        exchange.completeWithBankResponse("0xEXCTX0001", "BANK-TX-EXC-001");
    }
}
