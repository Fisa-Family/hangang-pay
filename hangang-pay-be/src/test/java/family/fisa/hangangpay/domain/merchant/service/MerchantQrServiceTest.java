package family.fisa.hangangpay.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.MerchantQrResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
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
class MerchantQrServiceTest {

    @Mock private PartyRepository partyRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock ObjectMapper objectMapper;

    @InjectMocks MerchantQrService merchantQrService;

    private static final Long PARTY_ID = 10L;
    private static final Long MERCHANT_ID = 1L;

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

    @Nested
    @DisplayName("가맹점 QR 조회 (getQrForPartyId)")
    class GetQrForPartyId {

        @Test
        @DisplayName("정상: 가맹점 partyId 로 base64 PNG QR 응답을 생성")
        void success() throws Exception {
            // given
            Party merchantParty = party(PARTY_ID, PartyType.MERCHANT);
            Merchant merchant = merchant(MERCHANT_ID, merchantParty);

            when(partyRepository.findById(PARTY_ID)).thenReturn(Optional.of(merchantParty));
            when(merchantRepository.findByParty_Id(PARTY_ID)).thenReturn(Optional.of(merchant));
            when(objectMapper.writeValueAsString(any()))
                    .thenReturn("{\"merchantId\":1,\"partyId\":10}");

            // when
            MerchantQrResponse result = merchantQrService.getQrForPartyId(PARTY_ID);

            // then
            assertThat(result.merchantId()).isEqualTo(MERCHANT_ID);
            assertThat(result.partyId()).isEqualTo(PARTY_ID);
            assertThat(result.merchantName()).isEqualTo("카페 드롭탑 강남점");
            assertThat(result.address()).isEqualTo("서울시 강남구 테헤란로 427");
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
    }
}
