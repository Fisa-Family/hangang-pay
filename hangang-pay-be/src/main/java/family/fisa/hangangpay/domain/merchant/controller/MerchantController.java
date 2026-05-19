package family.fisa.hangangpay.domain.merchant.controller;

import family.fisa.hangangpay.domain.merchant.dto.MerchantMyPageResponse;
import family.fisa.hangangpay.domain.merchant.dto.MerchantQrResponse;
import family.fisa.hangangpay.domain.merchant.service.MerchantQrService;
import family.fisa.hangangpay.domain.merchant.service.MerchantQueryService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.response.ApiResponse;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
@Tag(name = "Merchant", description = "가맹점 API")
public class MerchantController {

    private final MerchantQrService qrService;
    private final MerchantQueryService merchantQueryService;

    @Operation(summary = "가맹점 마이페이지 조회 (M-MY-01)")
    @GetMapping("/mypage")
    public ResponseEntity<ApiResponse<MerchantMyPageResponse>> getMyPage(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId) {

        MerchantMyPageResponse merchantMyPageResponse = merchantQueryService.getMyPage(partyId);

        return ResponseEntity.ok(
                ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, merchantMyPageResponse));
    }

    /** 현재 세션 가맹점의 결제용 QR을 조회한다. */
    @Operation(
            summary = "가맹점 QR 조회 (MERCHANT-007)",
            description = "현재 세션의 가맹점 식별값(merchantId, partyId)을 담은 PNG QR을 base64로 반환한다.")
    @GetMapping("/qr")
    public ResponseEntity<ApiResponse<MerchantQrResponse>> getMerchantQr(
            @SessionAttribute("partyId") Long partyId) {
        MerchantQrResponse response = qrService.getQrForPartyId(partyId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }
}
