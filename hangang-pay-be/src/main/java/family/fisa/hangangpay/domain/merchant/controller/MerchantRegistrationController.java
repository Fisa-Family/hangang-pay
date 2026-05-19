package family.fisa.hangangpay.domain.merchant.controller;

import family.fisa.hangangpay.domain.merchant.dto.BusinessInfoResponse;
import family.fisa.hangangpay.domain.merchant.service.MerchantQueryService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
public class MerchantRegistrationController {

    private final MerchantQueryService merchantQueryService;

    @GetMapping("/business-info")
    public ResponseEntity<ApiResponse<BusinessInfoResponse>> getBusinessInfo(
            @RequestParam String businessNumber) {
        BusinessInfoResponse response = merchantQueryService.getBusinessInfo(businessNumber);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }
}
