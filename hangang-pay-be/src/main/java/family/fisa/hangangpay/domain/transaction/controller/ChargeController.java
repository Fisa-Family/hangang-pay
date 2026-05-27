package family.fisa.hangangpay.domain.transaction.controller;

import family.fisa.hangangpay.domain.transaction.code.TransactionSuccessCode;
import family.fisa.hangangpay.domain.transaction.dto.request.ChargeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeInitResponse;
import family.fisa.hangangpay.domain.transaction.service.ChargeCommandService;
import family.fisa.hangangpay.domain.transaction.service.ChargeQueryService;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.response.ApiResponse;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@Tag(name = "충전", description = "충전 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChargeController {

    private final ChargeQueryService chargeQueryService;
    private final ChargeCommandService chargeCommandService;

    // 충전 초기화 정보 조회 - 한도, 할인율, 계좌 목록 반환 및 PENDING 거래 등록
    @Operation(summary = "충전 정보 조회", description = "충전 한도, 할인율, 계좌 목록을 조회하고 PENDING 거래를 등록합니다.")
    @GetMapping("/charge/init")
    public ResponseEntity<ApiResponse<ChargeInitResponse>> initCharge(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId) {
        return ResponseEntity.ok(
                ApiResponse.onSuccess(
                        TransactionSuccessCode.CHARGE_INFO_RETRIEVED,
                        chargeQueryService.getChargeInit(partyId)));
    }

    /** 충전 실행 */
    @Operation(summary = "충전 실행", description = "PENDING 충전 거래를 실행하고 결과를 반환합니다.")
    @PostMapping("/charge")
    public ResponseEntity<ApiResponse<ChargeExecuteResponse>> executeCharge(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long sessionPartyId,
            @Valid @RequestBody ChargeExecuteRequest request) {
        if (request.partyId() != null && !sessionPartyId.equals(request.partyId())) {
            throw new BusinessException(GeneralErrorCode.COMMON_FORBIDDEN);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.onSuccess(
                                TransactionSuccessCode.CHARGE_EXECUTED,
                                chargeCommandService.execute(sessionPartyId, request)));
    }
}
