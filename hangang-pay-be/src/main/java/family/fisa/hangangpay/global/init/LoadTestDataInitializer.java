package family.fisa.hangangpay.global.init;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.jpa.InstitutionJpaRepository;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.jpa.MerchantJpaRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.domain.wallet.service.WalletCommandService;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부하 테스트용 대량 유저/가맹점 시드. {@code loadtest} 프로필에서만 동작한다.
 *
 * <p>유저 i(1..N)마다 Party + User(phone {@code "010"+%08d(i)}, password/pin 고정) + 커스터디 지갑(bank 위임) +
 * 온체인 잔액 mint를 생성한다. 가맹점 i(1..N)도 별도 Party + Merchant + Wallet로 시드해, k6에서 VU별 1:1 결제
 * 대상 매핑을 할 수 있게 한다.
 *
 * <p>지갑 생성/민트가 온체인 트랜잭션이라 시드에 수 분이 걸릴 수 있다. bc(Besu)+컨트랙트와 bank가 먼저 떠 있어야 한다.
 */
@Slf4j
@Component
@Profile("loadtest")
@Order(2) // LocalDataInitializer(@Order(1)) 이후 실행되어 기준 가맹점을 재사용한다
@RequiredArgsConstructor
@SuppressWarnings("java:S2068") // 부하 테스트 전용 시드 계정, 운영 환경에 배포되지 않음
public class LoadTestDataInitializer implements ApplicationRunner {

    private static final String SEED_MERCHANT_PASSWORD = "password";
    private static final String SEED_PAYMENT_PIN = "123456";
    private static final String SEED_MERCHANT_BUSINESS_NUMBER = "1234567890";
    private static final String SEED_PASSWORD = "password";
    private static final Long WOORI_INSTITUTION_ID = 2L; // bankClient.localMint 발행 기관

    // 유저당 온체인 발행액. 전체 합계가 컨트랙트 MAX_TOTAL_ISSUANCE(1천만 토큰)를 넘으면 IssuanceLimitExceeded로 리버트되므로
    // userCount × mint 가 한도 이내여야 한다. (예: N=300 → 30000, N=1000 → 9000)
    @Value("${LOADTEST_MINT_AMOUNT:30000}")
    private BigDecimal mintAmount;

    @Value("${LOADTEST_MERCHANT_COUNT:1000}")
    private int merchantCount;

    private final PartyRepository partyRepository;
    private final UserRepository userRepository;
    private final MerchantJpaRepository merchantJpaRepository;
    private final InstitutionJpaRepository institutionJpaRepository;
    private final WalletRepository walletRepository;
    private final WalletCommandService walletCommandService;
    private final BankClient bankClient;
    private final PasswordEncoder passwordEncoder;

    @Value("${LOADTEST_USER_COUNT:1000}")
    private int userCount;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 1. 기준 시드(가맹점/기관)가 준비됐는지 확인
        Merchant merchant =
                merchantJpaRepository
                        .findByBusinessNumberWithParty(SEED_MERCHANT_BUSINESS_NUMBER)
                        .orElse(null);
        Institution bok = institutionJpaRepository.findById(1L).orElse(null);
        if (merchant == null || bok == null) {
            log.warn("[be-loadtest-seed] skipped: 기준 가맹점/기관 시드 없음 (local 프로필 함께 활성화 필요)");
            return;
        }
        Party merchantParty = merchant.getParty();
        log.info(
                "[be-loadtest-seed] start: userCount={}, merchantCount={}, baseMerchantPartyId={}",
                userCount,
                merchantCount,
                merchantParty.getId());

        // 2. 부하용 가맹점 N명 생성 (결제 대상 1:1 매핑용)
        seedMerchants(bok);

        // 3. 부하용 유저 N명 생성 (지갑 + 온체인 잔액 포함)
        int minted = 0;
        for (int i = 1; i <= userCount; i++) {
            if (seedUserIfMissing(i, bok)) {
                minted++;
            }

            if (i % 100 == 0) {
                log.info("[be-loadtest-seed] progress users={}/{}", i, userCount);
            }
        }

        log.info(
                "[be-loadtest-seed] done: merchants={}, users={}, minted={}",
                merchantCount,
                userCount,
                minted);
    }

    private void seedMerchants(Institution bok) {
        int created = 0;
        for (int i = 1; i <= merchantCount; i++) {
            String businessNumber = merchantBusinessNumber(i);
            Merchant existing =
                    merchantJpaRepository.findByBusinessNumberWithParty(businessNumber).orElse(null);
            if (existing != null) {
                ensureMerchantWallet(existing, bok);
                continue;
            }

            Party merchantParty = partyRepository.save(Party.of(PartyType.MERCHANT));
            merchantJpaRepository.save(
                    Merchant.builder()
                            .party(merchantParty)
                            .username(merchantUsername(i))
                            .passwordHash(passwordEncoder.encode(SEED_MERCHANT_PASSWORD))
                            .paymentPinHash(passwordEncoder.encode(SEED_PAYMENT_PIN))
                            .businessNumber(businessNumber)
                            .merchantName(merchantName(i))
                            .ownerName(merchantOwnerName(i))
                            .phoneNumber(merchantPhoneNumber(i))
                            .address(merchantAddress(i))
                            .latitude(new BigDecimal("37.5635030"))
                            .longitude(new BigDecimal("127.0369670"))
                            .build());
            walletCommandService.createWallet(merchantParty, bok);
            created++;

            if (i % 100 == 0) {
                log.info("[be-loadtest-seed] progress merchants={}/{}", i, merchantCount);
            }
        }

        log.info("[be-loadtest-seed] merchants ready: created={}, target={}", created, merchantCount);
    }

    private boolean seedUserIfMissing(int index, Institution bok) {
        String phoneNumber = String.format("010%08d", index);
        if (userRepository.findByPhoneNumberWithParty(phoneNumber).isPresent()) {
            return false;
        }

        Party userParty = partyRepository.save(Party.of(PartyType.USER));
        userRepository.save(
                User.builder()
                        .party(userParty)
                        .username("부하유저" + index)
                        .passwordHash(passwordEncoder.encode(SEED_PASSWORD))
                        .paymentPinHash(passwordEncoder.encode(SEED_PAYMENT_PIN))
                        .phoneNumber(phoneNumber)
                        .birthDate(LocalDate.of(1995, 1, 1))
                        .region("성동구")
                        .build());
        Wallet wallet = walletCommandService.createWallet(userParty, bok);
        return syncOnChainBalance(wallet.getAddress());
    }

    private void ensureMerchantWallet(Merchant merchant, Institution bok) {
        Long partyId = merchant.getParty().getId();
        if (walletRepository.findByParty_Id(partyId).isPresent()) {
            return;
        }
        walletCommandService.createWallet(merchant.getParty(), bok);
    }

    private static String merchantBusinessNumber(int index) {
        return String.format("88%08d", index);
    }

    private static String merchantUsername(int index) {
        return "부하가맹점" + index;
    }

    private static String merchantName(int index) {
        return "부하가맹점" + index;
    }

    private static String merchantOwnerName(int index) {
        return "부하대표" + index;
    }

    private static String merchantPhoneNumber(int index) {
        return String.format("021%07d", index);
    }

    private static String merchantAddress(int index) {
        return "서울시 성동구 왕십리로 " + (100 + index);
    }

    // seed 유저 지갑에 온체인 잔액 mint (로컬 전용). 실패해도 시드는 계속.
    private boolean syncOnChainBalance(String walletAddress) {
        try {
            bankClient.localMint(WOORI_INSTITUTION_ID, walletAddress, mintAmount);
            return true;
        } catch (Exception e) {
            log.warn("[be-loadtest-seed] 온체인 mint 실패. wallet={}", walletAddress, e);
            return false;
        }
    }
}
