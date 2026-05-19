package family.fisa.hangangpay.domain.merchant.controller;

import family.fisa.hangangpay.domain.account.dto.MerchantAccountUpdateRequest;
import family.fisa.hangangpay.domain.account.dto.MerchantAccountUpdateResponse;
import family.fisa.hangangpay.domain.account.service.AccountCommandService;
import family.fisa.hangangpay.domain.merchant.dto.MerchantMyPageResponse;
import family.fisa.hangangpay.domain.merchant.service.MerchantQueryService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.response.ApiResponse;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@Tag(name = "가맹점 마이페이지")
@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantQueryService merchantQueryService;
    private final AccountCommandService accountCommandService;

    @Operation(summary = "가맹점 마이페이지 조회 (MERCHANT-009)")
    @GetMapping("/mypage")
    public ResponseEntity<ApiResponse<MerchantMyPageResponse>> getMyPage(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId) {

        MerchantMyPageResponse merchantMyPageResponse = merchantQueryService.getMyPage(partyId);

        return ResponseEntity.ok(
                ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, merchantMyPageResponse));
    }

    @Operation(summary = "가맹점 계좌 변경 (MERCHANT-010)")
    @PatchMapping("/accounts")
    public ResponseEntity<ApiResponse<MerchantAccountUpdateResponse>> updateAccount(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @Valid @RequestBody MerchantAccountUpdateRequest request) {

        MerchantAccountUpdateResponse response =
                accountCommandService.updateMerchantSettlementAccount(partyId, request);

        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }
}
