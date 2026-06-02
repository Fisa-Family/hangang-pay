package family.fisa.hangangpay.domain.transaction.controller;

import family.fisa.hangangpay.domain.transaction.code.TransactionSuccessCode;
import family.fisa.hangangpay.domain.transaction.dto.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeInitResponse;
import family.fisa.hangangpay.domain.transaction.service.ExchangeCommandService;
import family.fisa.hangangpay.domain.transaction.service.ExchangeQueryService;
import family.fisa.hangangpay.global.response.ApiResponse;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "환전", description = "환전 API")
@RestController
@RequestMapping("/api/v1/exchange")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeCommandService exchangeCommandService;
    private final ExchangeQueryService exchangeQueryService;

    /** 사용자의 환전 가능 여부를 조회한다. */
    @Operation(
            summary = "환전 정보 조회 (EXCHANGE-001)",
            description = "환전 가능 여부와 현재 지갑 잔액을 반환한다. 최근 충전액의 60% 이상 사용 여부로 자격을 판단.")
    @GetMapping("/init")
    public ResponseEntity<ApiResponse<ExchangeInitResponse>> getExchangeInit(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId) {
        ExchangeInitResponse response = exchangeQueryService.getExchangeInit(partyId);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.EXCHANGE_INFO_RETRIEVED, response));
    }

    /** 사용자가 사용하지 않은 금액에 대한 환전을 진행한다. */
    @Operation(
            summary = "사용자 환전 진행 (EXCHANGE-002)",
            description = "최근 충전액의 60% 이상을 사용한 소비자가 보유한 토큰은 1:1 비율로 계좌에 환전한다.")
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<ExchangeExecuteResponse>> executeUserExchange(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @Valid @RequestBody ExchangeExecuteRequest request) {
        ExchangeExecuteResponse response =
                exchangeCommandService.executeUserExchange(partyId, request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.EXCHANGE_EXECUTED, response));
    }
}
