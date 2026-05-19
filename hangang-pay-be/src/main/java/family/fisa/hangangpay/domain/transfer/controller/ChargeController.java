package family.fisa.hangangpay.domain.transfer.controller;

import family.fisa.hangangpay.domain.transfer.code.TransferSuccessCode;
import family.fisa.hangangpay.domain.transfer.dto.ChargeCalculateRequest;
import family.fisa.hangangpay.domain.transfer.dto.ChargeCalculateResponse;
import family.fisa.hangangpay.domain.transfer.dto.ChargeExecuteRequest;
import family.fisa.hangangpay.domain.transfer.dto.ChargeReceiptResponse;
import family.fisa.hangangpay.domain.transfer.dto.ChargeLimitResponse;
import family.fisa.hangangpay.domain.transfer.service.FundTransferCommandService;
import family.fisa.hangangpay.domain.transfer.service.FundTransferQueryService;
import family.fisa.hangangpay.global.response.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 충전 관련 HTTP 요청 처리 컨트롤러 */
@Tag(name = "충전", description = "충전 API")
@RestController
@RequestMapping("/api/v1/charge")
@RequiredArgsConstructor
public class ChargeController {

    /** 충전 조회 서비스 */
    private final FundTransferQueryService fundTransferQueryService;

    /** 충전 실행 서비스 */
    private final FundTransferCommandService fundTransferCommandService;

    /** CHARGE-001 충전 한도 조회 엔드포인트 */
    @Operation(summary = "충전 한도 조회 (CHARGE-001)", description = "이번 달 충전 사용액과 잔여 한도를 조회한다.")
    @GetMapping("/limit")
    public ResponseEntity<ApiResponse<ChargeLimitResponse>> getChargeLimit(
            // TODO: 로그인 구현 후 HttpSession session 파라미터로 교체 및 아래 세션 인증 블록 주석 해제
            @RequestParam Long partyId) {

        // TODO: 로그인 구현 후 아래 세션 인증 블록 주석 해제
        // Long partyId = (Long) session.getAttribute("partyId");
        // if (partyId == null) {
        //     throw new BusinessException(GeneralErrorCode.UNAUTHORIZED_401);
        // }

        // 충전 한도 조회 후 응답 반환
        ChargeLimitResponse response = fundTransferQueryService.getChargeLimit(partyId);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransferSuccessCode.CHARGE_LIMIT_RETRIEVED, response));
    }

    /** CHARGE-002 충전 금액 및 할인 계산 엔드포인트 */
    @Operation(
            summary = "충전 금액 및 할인 계산 (CHARGE-002)",
            description = "충전 금액 입력 시 할인율 10% 적용 후 실 결제 금액을 계산한다.")
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<ChargeCalculateResponse>> calculateCharge(
            // TODO: 로그인 구현 후 HttpSession session 파라미터로 교체 및 아래 세션 인증 블록 주석 해제
            @RequestParam Long partyId, @Valid @RequestBody ChargeCalculateRequest request) {

        // TODO: 로그인 구현 후 아래 세션 인증 블록 주석 해제
        // Long partyId = (Long) session.getAttribute("partyId");
        // if (partyId == null) {
        //     throw new BusinessException(GeneralErrorCode.UNAUTHORIZED_401);
        // }

        // 충전 금액 계산 후 응답 반환
        ChargeCalculateResponse response =
                fundTransferQueryService.calculateCharge(partyId, request.getChargeAmount());
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransferSuccessCode.CHARGE_CALCULATED, response));
    }

    /** CHARGE-003 충전 실행 엔드포인트 */
    @Operation(
            summary = "충전 실행 (CHARGE-003)",
            description = "발행 가능량 검증 후 계좌를 차감한다. 충전 영수증을 발급한다.")
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<ChargeReceiptResponse>> executeCharge(
            // TODO: 로그인 구현 후 HttpSession session 파라미터로 교체 및 아래 세션 인증 블록 주석 해제
            @RequestParam Long partyId, @Valid @RequestBody ChargeExecuteRequest request) {

        // TODO: 로그인 구현 후 아래 세션 인증 블록 주석 해제
        // Long partyId = (Long) session.getAttribute("partyId");
        // if (partyId == null) {
        //     throw new BusinessException(GeneralErrorCode.UNAUTHORIZED_401);
        // }

        // 충전 실행 후 응답 반환
        ChargeReceiptResponse response = fundTransferCommandService.executeCharge(partyId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(TransferSuccessCode.CHARGE_EXECUTED, response));
    }
}
