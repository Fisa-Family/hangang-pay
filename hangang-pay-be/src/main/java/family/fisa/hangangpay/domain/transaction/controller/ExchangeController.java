package family.fisa.hangangpay.domain.transaction.controller;

import family.fisa.hangangpay.domain.transaction.code.TransactionSuccessCode;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.ExchangeIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeInitResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeIntentResponse;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeCommandService;
import family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeQueryService;
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

    /** 환전 가능 여부/잔액 조회 */
    @Operation(summary = "환전 정보 조회 (EXCHANGE-001)", description = "환전 가능 여부와 지갑 잔액을 반환한다.")
    @GetMapping("/init")
    public ResponseEntity<ApiResponse<ExchangeInitResponse>> getExchangeInit(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId) {
        ExchangeInitResponse response = exchangeQueryService.getExchangeInit(partyId);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.EXCHANGE_INFO_RETRIEVED, response));
    }

    /** 환전 intent 생성 (PIN 없음) */
    @Operation(
            summary = "환전 의도 생성 (EXCHANGE-002)",
            description = "PENDING 환전 의도를 생성한다. 자격(60%) 검증 포함.")
    @PostMapping("/intents")
    public ResponseEntity<ApiResponse<ExchangeIntentResponse>> createIntent(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @Valid @RequestBody ExchangeIntentCreateRequest request) {
        ExchangeIntentResponse response = exchangeCommandService.createUserIntent(partyId, request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.EXCHANGE_INTENT_CREATED, response));
    }

    /** 환전 실행 (PIN) */
    @Operation(summary = "환전 실행 (EXCHANGE-003)", description = "PIN 검증 후 생성된 의도를 1:1로 계좌 환전한다.")
    @PostMapping("/{transactionUuid}/execute")
    public ResponseEntity<ApiResponse<ExchangeExecuteResponse>> executeUserExchange(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @PathVariable String transactionUuid,
            @Valid @RequestBody ExchangeExecuteRequest request) {
        ExchangeExecuteResponse response =
                exchangeCommandService.executeUserExchange(partyId, transactionUuid, request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.EXCHANGE_EXECUTED, response));
    }
}
