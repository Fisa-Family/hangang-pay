package family.fisa.hangangpay.domain.transaction.controller;

import family.fisa.hangangpay.domain.transaction.code.TransactionSuccessCode;
import family.fisa.hangangpay.domain.transaction.dto.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.service.ExchangeCommandService;
import family.fisa.hangangpay.global.response.ApiResponse;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@Tag(name = "환전", description = "환전 API")
@RestController
@RequestMapping("/api/v1/exchange")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeCommandService exchangeCommandService;

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
