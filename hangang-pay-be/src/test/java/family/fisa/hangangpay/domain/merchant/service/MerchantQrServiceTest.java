package family.fisa.hangangpay.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.MerchantQrResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantQrServiceTest {

    @Mock private PartyRepository partyRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private WalletRepository walletRepository;

    private MerchantQrService merchantQrService;

    private static final Long PARTY_ID = 10L;
    private static final Long MERCHANT_ID = 1L;
    private static final String WALLET_ADDRESS = "0xAbCdEf1234567890aBcDeF1234567890AbCdEf12";

    @BeforeEach
    void setUp() {
        // ObjectMapper는 진짜 객체 — 실제 JSON 직렬화가 일어나야 QR이 만들어지고
        // qrImageBase64 prefix 검증이 의미를 가짐.
        merchantQrService =
                new MerchantQrService(
                        partyRepository, merchantRepository, walletRepository, new ObjectMapper());
    }

    private Party party(Long partyId, PartyType type) {
        return Party.builder().id(partyId).partyType(type).build();
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

    @Nested
    @DisplayName("가맹점 QR 조회 (getQrForPartyId)")
    class GetQrForPartyId {

        @Test
        @DisplayName("정상: 가맹점 partyId 로 base64 PNG QR 응답을 생성")
        void success() {
            // given
            Party merchantParty = party(PARTY_ID, PartyType.MERCHANT);
            Merchant merchant = merchant(MERCHANT_ID, merchantParty);
            Wallet wallet = wallet(merchantParty, WALLET_ADDRESS);

            when(partyRepository.findById(PARTY_ID)).thenReturn(Optional.of(merchantParty));
            when(merchantRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(merchant));
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(wallet));

            // when
            MerchantQrResponse result = merchantQrService.getQrForPartyId(PARTY_ID);

            // then
            assertThat(result.qrImageBase64()).startsWith("data:image/png;base64,");
            assertThat(result.qrImageBase64().length())
                    .isGreaterThan("data:image/png;base64,".length());
        }

        @Test
        @DisplayName("Party 없음 -> MERCHANT_NOT_FOUND")
        void throws_whenPartyNotFound() {
            // given
            when(partyRepository.findById(PARTY_ID)).thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(() -> merchantQrService.getQrForPartyId(PARTY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", MerchantErrorCode.MERCHANT_NOT_FOUND);
        }

        @Test
        @DisplayName("PartyType 이 USER -> FORBIDDEN_MERCHANT")
        void throws_whenPartyIsUser() {
            // given
            Party userParty = party(PARTY_ID, PartyType.USER);
            when(partyRepository.findById(PARTY_ID)).thenReturn(Optional.of(userParty));

            // when, then
            assertThatThrownBy(() -> merchantQrService.getQrForPartyId(PARTY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", MerchantErrorCode.FORBIDDEN_MERCHANT);
        }

        @Test
        @DisplayName("Party 는 MERCHANT 지만 Merchant 레코드 없음 -> MERCHANT_NOT_FOUND")
        void throws_whenMerchantRecordMissing() {
            // given
            Party merchantParty = party(PARTY_ID, PartyType.MERCHANT);
            when(partyRepository.findById(PARTY_ID)).thenReturn(Optional.of(merchantParty));
            when(merchantRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(() -> merchantQrService.getQrForPartyId(PARTY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", MerchantErrorCode.MERCHANT_NOT_FOUND);
        }

        @Test
        @DisplayName("Wallet 이 존재하지 않음 -> MERCHANT_NOT_FOUND")
        void throws_whenWalletMissing() {
            // given
            Party merchantParty = party(PARTY_ID, PartyType.MERCHANT);
            Merchant merchant = merchant(MERCHANT_ID, merchantParty);
            when(partyRepository.findById(PARTY_ID)).thenReturn(Optional.of(merchantParty));
            when(merchantRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(merchant));
            when(walletRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(() -> merchantQrService.getQrForPartyId(PARTY_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", MerchantErrorCode.MERCHANT_NOT_FOUND);
        }
    }
}
