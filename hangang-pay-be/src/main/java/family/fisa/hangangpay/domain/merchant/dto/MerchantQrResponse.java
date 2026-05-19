package family.fisa.hangangpay.domain.merchant.dto;

import lombok.Builder;

@Builder
public record MerchantQrResponse(String qrImageBase64) {

    public static MerchantQrResponse of(String qrImageBase64) {
        return MerchantQrResponse.builder().qrImageBase64(qrImageBase64).build();
    }
}
