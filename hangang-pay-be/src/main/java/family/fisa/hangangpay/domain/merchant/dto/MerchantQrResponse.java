package family.fisa.hangangpay.domain.merchant.dto;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.party.entity.Party;
import lombok.Builder;

@Builder
public record MerchantQrResponse(
        Long merchantId, Long partyId, String merchantName, String address, String qrImageBase64) {

    public static MerchantQrResponse from(Merchant merchant, Party party, String qrImageBase64) {
        return MerchantQrResponse.builder()
                .merchantId(merchant.getId())
                .partyId(party.getId())
                .merchantName(merchant.getMerchantName())
                .address(merchant.getAddress())
                .qrImageBase64(qrImageBase64)
                .build();
    }
}
