package family.fisa.hangangpay.domain.transaction.controller;

import family.fisa.hangangpay.domain.transaction.code.TransactionSuccessCode;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeInitResponse;
import family.fisa.hangangpay.domain.transaction.service.ChargeQueryService;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.response.ApiResponse;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@Tag(name = "충전", description = "충전 API")
@RestController
@RequestMapping("/api/v1/charge")
@RequiredArgsConstructor
public class ChargeController {

    private final ChargeQueryService chargeQueryService;

    // 충전 초기화 정보 조회 - 한도, 할인율, 계좌 목록 반환 및 PENDING 거래 등록
    @Operation(summary = "충전 정보 조회", description = "충전 한도, 할인율, 계좌 목록을 조회하고 PENDING 거래를 등록합니다.")
    @GetMapping("/{partyId}/init")
    public ResponseEntity<ApiResponse<ChargeInitResponse>> initCharge(
            @PathVariable Long partyId,
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long sessionPartyId) {
        if (!partyId.equals(sessionPartyId)) {
            throw new BusinessException(GeneralErrorCode.COMMON_FORBIDDEN);
        }
        return ResponseEntity.ok(
                ApiResponse.onSuccess(
                        TransactionSuccessCode.CHARGE_INFO_RETRIEVED,
                        chargeQueryService.getChargeInit(partyId)));
    }
}
