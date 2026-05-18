package family.fisa.hangangpay.domain.transfer.controller;

import family.fisa.hangangpay.domain.transfer.code.TransferSuccessCode;
import family.fisa.hangangpay.domain.transfer.dto.ChargeCalculateRequest;
import family.fisa.hangangpay.domain.transfer.dto.ChargeCalculateResponse;
import family.fisa.hangangpay.domain.transfer.dto.ChargeLimitResponse;
import family.fisa.hangangpay.domain.transfer.service.FundTransferService;
import family.fisa.hangangpay.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

/** 충전 관련 HTTP 요청 처리 컨트롤러 */
@Tag(name = "Charge", description = "Charge API")
@RestController
@RequestMapping("/api/v1/charge")
@RequiredArgsConstructor
public class ChargeController {

    /** 자금 이체 서비스 */
    private final FundTransferService fundTransferService;

    /** CHARGE-001 충전 한도 조회 엔드포인트 */
    @Operation(summary = "충전 한도 조회", description = "이번 달 충전 사용액과 잔여 한도를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 필요")
    })
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
        ChargeLimitResponse response = fundTransferService.getChargeLimit(partyId);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransferSuccessCode.CHARGE_LIMIT_RETRIEVED, response));
    }

    /** CHARGE-002 충전 금액 및 할인 계산 엔드포인트 */
    @Operation(summary = "충전 금액 및 할인 계산", description = "충전 금액 입력 시 할인율 10% 적용 후 실 결제 금액을 계산합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "계산 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "만원 단위 오류 또는 한도 초과"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 필요")
    })
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
                fundTransferService.calculateCharge(partyId, request.getChargeAmount());
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransferSuccessCode.CHARGE_CALCULATED, response));
    }
}
