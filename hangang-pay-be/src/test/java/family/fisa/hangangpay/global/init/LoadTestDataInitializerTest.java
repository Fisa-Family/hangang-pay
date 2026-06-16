package family.fisa.hangangpay.global.init;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LoadTestDataInitializerTest {

    @Mock private PartyRepository partyRepository;
    @Mock private UserRepository userRepository;
    @Mock private MerchantJpaRepository merchantJpaRepository;
    @Mock private InstitutionJpaRepository institutionJpaRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletCommandService walletCommandService;
    @Mock private BankClient bankClient;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private LoadTestDataInitializer initializer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(initializer, "merchantCount", 2);
        ReflectionTestUtils.setField(initializer, "userCount", 2);
        ReflectionTestUtils.setField(initializer, "mintAmount", new BigDecimal("30000"));

        when(institutionJpaRepository.findById(1L))
                .thenReturn(
                        Optional.of(
                                Institution.builder()
                                        .id(1L)
                                        .institutionCode("BoK")
                                        .institutionName("한국은행")
                                        .build()));
        when(merchantJpaRepository.findByBusinessNumberWithParty(anyString()))
                .thenAnswer(
                        invocation -> {
                            String businessNumber = invocation.getArgument(0);
                            if ("1234567890".equals(businessNumber)) {
                                return Optional.of(seedMerchant());
                            }
                            return Optional.empty();
                        });
        when(userRepository.findByPhoneNumberWithParty(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "hash-" + invocation.getArgument(0));
        when(partyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(merchantJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletCommandService.createWallet(any(), any()))
                .thenAnswer(
                        invocation -> {
                            Party party = invocation.getArgument(0);
                            Institution institution = invocation.getArgument(1);
                            return Wallet.builder()
                                    .party(party)
                                    .institution(institution)
                                    .address("0x" + party.getPartyType().name().toLowerCase() + System.nanoTime())
                                    .build();
                        });
        doNothing().when(bankClient).localMint(anyLong(), anyString(), any());
    }

    @Test
    void seedsMerchantsAndUsersForLoadTest() {
        initializer.run(null);

        verify(merchantJpaRepository, times(2)).save(any(Merchant.class));
        verify(userRepository, times(2)).save(any(User.class));
        verify(walletCommandService, times(4)).createWallet(any(Party.class), any(Institution.class));
        verify(bankClient, times(2)).localMint(eq(2L), anyString(), eq(new BigDecimal("30000")));
        verify(partyRepository, times(4)).save(any(Party.class));
    }

    private static Merchant seedMerchant() {
        Party merchantParty = Party.builder().id(2L).partyType(PartyType.MERCHANT).build();
        return Merchant.builder()
                .party(merchantParty)
                .username("테스트가맹점")
                .passwordHash("hash-password")
                .paymentPinHash("hash-123456")
                .businessNumber("1234567890")
                .merchantName("한강떡볶이")
                .ownerName("홍길동")
                .phoneNumber("0226001234")
                .address("서울시 성동구 왕십리로 222")
                .latitude(new BigDecimal("37.5635030"))
                .longitude(new BigDecimal("127.0369670"))
                .build();
    }
}
