package family.fisa.hangangpay.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.merchant.code.error.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.MerchantInfoResponse;
import family.fisa.hangangpay.domain.merchant.dto.MerchantMyPageResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MerchantQueryServiceTest {

    @Mock MerchantRepository merchantRepository;
    @Mock AccountRepository accountRepository;
    @Mock private WalletRepository walletRepository;

    @Mock Institution institution;

    @InjectMocks MerchantQueryService merchantQueryService;

    private static final Long MERCHANT_ID = 1L;
    private static final Long PARTY_ID = 10L;
    private static final String WALLET_ADDRESS = "0xAbCdEf1234567890aBcDeF1234567890AbCdEf12";
    private static final String ACCOUNT_NUMBER = "1002456789012";
    private static final String INSTITUTION_NAME = "우리은행";
    private static final String INSTITUTION_CODE = "020";

    private Party party(Long partyId) {
        return Party.builder().id(partyId).partyType(PartyType.MERCHANT).build();
    }

    private Merchant merchant(Long merchantId, Party party) {
        return Merchant.builder()
                .id(merchantId)
                .party(party)
                .username("dropTop01")
                .passwordHash("hash")
                .businessNumber("123-45-67890")
                .merchantName("카페 드롭탑 강남점")
                .ownerName("홍길동")
                .address("서울시 강남구 테헤란로 427")
                .build();
    }

    private Wallet wallet(Party party, String address) {
        return Wallet.builder().party(party).address(address).build();
    }

    private Institution institution() {
        return Institution.builder()
                .institutionCode(INSTITUTION_CODE)
                .institutionName(INSTITUTION_NAME)
                .build();
    }

    private Account settlementAccount(Party party) {
        return Account.builder()
                .party(party)
                .institution(institution())
                .accountType(AccountType.SETTLEMENT)
                .accountNumber(ACCOUNT_NUMBER)
                .build();
    }

    @Nested
    @DisplayName("Mypage")
    class MyPage {

        @Test
        void 가맹점_없을_경우() {
            // given
            given(merchantRepository.findByParty_Id(anyLong())).willReturn(Optional.empty());

            // when & then.
            assertThatThrownBy(() -> merchantQueryService.getMyPage(1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(MerchantErrorCode.MERCHANT_NOT_FOUND);
        }

        @Test
        void 정산계좌_없을_경우() {
            // given
            given(merchantRepository.findByParty_Id(anyLong()))
                    .willReturn(Optional.of(mock(Merchant.class)));
            given(
                            accountRepository.findByParty_IdAndAccountType(
                                    anyLong(), eq(AccountType.SETTLEMENT)))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> merchantQueryService.getMyPage(1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(MerchantErrorCode.MERCHANT_SETTLEMENT_ACCOUNT_NOT_FOUND);
        }

        @Test
        void 정상_조회() {
            // given

            given(institution.getInstitutionName()).willReturn("국민은행");

            Party party = mock(Party.class);

            given(party.getId()).willReturn(10L);

            Merchant merchant = Merchant.builder().id(1L).party(party).build();

            Account account =
                    Account.builder()
                            .id(1L)
                            .institution(institution)
                            .accountType(AccountType.SETTLEMENT)
                            .accountNumber("1234567890")
                            .build();

            given(merchantRepository.findByParty_Id(anyLong())).willReturn(Optional.of(merchant));
            given(
                            accountRepository.findByParty_IdAndAccountType(
                                    anyLong(), eq(AccountType.SETTLEMENT)))
                    .willReturn(Optional.of(account));

            // when
            MerchantMyPageResponse result = merchantQueryService.getMyPage(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.merchantId()).isEqualTo(1L);
            assertThat(result.settlementAccount()).isNotNull();
        }
    }

    @Nested
    @DisplayName("가맹점 정보 조회 (getMerchantInfo)")
    class GetMerchantInfo {

        @Test
        @DisplayName("정상: merchantId로 가맹점 정보 + 지갑 주소를 반환")
        void success() {
            // given
            Party party = party(PARTY_ID);
            Merchant merchant = merchant(MERCHANT_ID, party);
            Wallet wallet = wallet(party, WALLET_ADDRESS);

            when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(wallet));

            // when
            MerchantInfoResponse result = merchantQueryService.getMerchantInfo(MERCHANT_ID);

            // then
            assertThat(result.merchantId()).isEqualTo(MERCHANT_ID);
            assertThat(result.merchantName()).isEqualTo("카페 드롭탑 강남점");
            assertThat(result.address()).isEqualTo("서울시 강남구 테헤란로 427");
            assertThat(result.walletAddress()).isEqualTo(WALLET_ADDRESS);
        }

        @Test
        @DisplayName("Merchant 없음 -> MERCHANT_NOT_FOUND")
        void throws_whenMerchantNotFound() {
            // given
            when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(() -> merchantQueryService.getMerchantInfo(MERCHANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", MerchantErrorCode.MERCHANT_NOT_FOUND);
        }

        @Test
        @DisplayName("Wallet 없음 -> MERCHANT_NOT_FOUND")
        void throws_whenWalletMissing() {
            // given
            Party party = party(PARTY_ID);
            Merchant merchant = merchant(MERCHANT_ID, party);

            when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(() -> merchantQueryService.getMerchantInfo(MERCHANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", MerchantErrorCode.MERCHANT_NOT_FOUND);
        }
    }
}
