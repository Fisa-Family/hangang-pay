package family.fisa.hangangpay.domain.merchant.dto;

import lombok.Builder;

/** QR 이미지에 인코딩되는 페이로드 위조 가능한 신뢰할 수 없는 입력이므로, 그런 정보는 손님 앱이 백엔드에서 별도로 조회하여 신뢰 가능한 출처에서 받아야 한다. */
@Builder
public record MerchantQrPayload(Long merchantId) {
    public static MerchantQrPayload of(Long merchantId) {
        return MerchantQrPayload.builder().merchantId(merchantId).build();
    }
}
