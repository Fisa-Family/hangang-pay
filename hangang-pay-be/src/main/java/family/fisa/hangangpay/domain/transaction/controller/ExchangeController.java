package family.fisa.hangangpay.domain.transaction.controller;

import family.fisa.hangangpay.domain.transaction.code.TransactionSuccessCode;
import family.fisa.hangangpay.domain.transaction.dto.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeReceiptResponse;
import family.fisa.hangangpay.domain.transaction.service.TransactionCommandService;
import family.fisa.hangangpay.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "환전", description = "환전 API")
@RestController
@RequestMapping("/api/v1/exchange")
@RequiredArgsConstructor
public class ExchangeController {

    private final TransactionCommandService transactionCommandService;

    @Operation(summary = "환전 실행", description = "한강페이 토큰 burn + 은행 계좌 입금을 동기 처리한다.")
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<ExchangeReceiptResponse>> executeExchange(
            @RequestParam Long partyId, @Valid @RequestBody ExchangeExecuteRequest request) {
        ExchangeReceiptResponse response = transactionCommandService.exchange(partyId, request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.EXCHANGE_EXECUTED, response));
    }
}
