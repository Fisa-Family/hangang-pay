package family.fisa.hangangpay.domain.transaction.controller;

import family.fisa.hangangpay.domain.transaction.code.TransactionSuccessCode;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ChargeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ChargeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeInitResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ChargeIntentResponse;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeCommandService;
import family.fisa.hangangpay.domain.transaction.service.charge.ChargeQueryService;
import family.fisa.hangangpay.global.response.ApiResponse;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@Tag(name = "충전", description = "충전 API")
@RestController
@RequestMapping("/api/v1/charge")
@RequiredArgsConstructor
public class ChargeController {

    private final ChargeQueryService chargeQueryService;
    private final ChargeCommandService chargeCommandService;

    /** 충전 초기화 정보 조회 - 한도, 할인율, 계좌 목록 반환 (조회 전용) */
    @Operation(summary = "충전 정보 조회 (CHARGE-001)", description = "충전 한도, 할인율, 계좌 목록을 조회한다.")
    @GetMapping("/init")
    public ResponseEntity<ApiResponse<ChargeInitResponse>> initCharge(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId) {
        return ResponseEntity.ok(
                ApiResponse.onSuccess(
                        TransactionSuccessCode.CHARGE_INFO_RETRIEVED,
                        chargeQueryService.getChargeInit(partyId)));
    }

    /** 충전 intent 생성 (PIN 없음) */
    @Operation(
            summary = "충전 의도 생성 (CHARGE-002)",
            description = "PENDING 충전 의도를 생성한다. 금액·출금 계좌 바인딩.")
    @PostMapping("/intents")
    public ResponseEntity<ApiResponse<ChargeIntentResponse>> createIntent(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @Valid @RequestBody ChargeIntentCreateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.onSuccess(
                        TransactionSuccessCode.CHARGE_INTENT_CREATED,
                        chargeCommandService.createIntent(partyId, request)));
    }

    /** 충전 실행 (PIN) */
    @Operation(summary = "충전 실행 (CHARGE-003)", description = "PIN 검증 후 생성된 의도를 실행한다.")
    @PostMapping("/{transactionUuid}/execute")
    public ResponseEntity<ApiResponse<ChargeExecuteResponse>> executeCharge(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @PathVariable String transactionUuid,
            @Valid @RequestBody ChargeExecuteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.onSuccess(
                                TransactionSuccessCode.CHARGE_EXECUTED,
                                chargeCommandService.execute(partyId, transactionUuid, request)));
    }
}
