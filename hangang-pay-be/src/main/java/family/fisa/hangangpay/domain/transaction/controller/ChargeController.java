package family.fisa.hangangpay.domain.transaction.controller;

import family.fisa.hangangpay.domain.transaction.code.TransactionSuccessCode;
import family.fisa.hangangpay.domain.transaction.dto.request.ChargeCalculateRequest;
import family.fisa.hangangpay.domain.transaction.dto.request.ChargeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeCalculateResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeLimitResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeReceiptResponse;
import family.fisa.hangangpay.domain.transaction.service.TransactionCommandService;
import family.fisa.hangangpay.domain.transaction.service.TransactionQueryService;
import family.fisa.hangangpay.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "충전", description = "충전 API")
@RestController
@RequestMapping("/api/v1/charge")
@RequiredArgsConstructor
public class ChargeController {

    private final TransactionQueryService transactionQueryService;
    private final TransactionCommandService transactionCommandService;

    @Operation(summary = "충전 한도 조회 (CHARGE-001)", description = "이번 달 충전 사용액과 잔여 한도를 조회한다.")
    @GetMapping("/limit")
    public ResponseEntity<ApiResponse<ChargeLimitResponse>> getChargeLimit(
            @RequestParam Long partyId) {
        ChargeLimitResponse response = transactionQueryService.getChargeLimit(partyId);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.CHARGE_LIMIT_RETRIEVED, response));
    }

    @Operation(
            summary = "충전 금액 및 할인 계산 (CHARGE-002)",
            description = "충전 금액 입력 시 할인율 10% 적용 후 실 결제 금액을 계산한다.")
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<ChargeCalculateResponse>> calculateCharge(
            @RequestParam Long partyId, @Valid @RequestBody ChargeCalculateRequest request) {
        ChargeCalculateResponse response =
                transactionQueryService.calculateCharge(partyId, request.getChargeAmount());
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.CHARGE_CALCULATED, response));
    }

    @Operation(summary = "충전 실행 (CHARGE-003)", description = "은행 계좌 출금 + 한강페이 토큰 mint를 동기 처리한다.")
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<ChargeReceiptResponse>> executeCharge(
            @RequestParam Long partyId, @Valid @RequestBody ChargeExecuteRequest request) {
        ChargeReceiptResponse response = transactionCommandService.charge(partyId, request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.CHARGE_EXECUTED, response));
    }
}
