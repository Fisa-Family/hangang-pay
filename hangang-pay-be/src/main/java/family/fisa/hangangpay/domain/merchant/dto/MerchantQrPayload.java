package family.fisa.hangangpay.domain.merchant.dto;

import lombok.Builder;

/** QR 이미지에 인코딩되는 페이로드 */
@Builder
public record MerchantQrPayload(Long merchantId, Long partyId) {

    public static MerchantQrPayload of(Long merchantId, Long partyId) {
        return MerchantQrPayload.builder().merchantId(merchantId).partyId(partyId).build();
    }
}
