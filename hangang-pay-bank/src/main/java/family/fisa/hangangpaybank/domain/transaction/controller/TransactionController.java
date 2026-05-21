package family.fisa.hangangpaybank.domain.transaction.controller;

import family.fisa.hangangpaybank.domain.transaction.code.TransactionSuccessCode;
import family.fisa.hangangpaybank.domain.transaction.dto.request.CancelRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ChargeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentResponse;
import family.fisa.hangangpaybank.domain.transaction.service.TransactionCommandService;
import family.fisa.hangangpaybank.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transaction", description = "거래(충전/환전/결제/취소) API")
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionCommandService transactionCommandService;

    @Operation(summary = "충전", description = "은행 계좌 잔액을 차감하고 한강페이 토큰을 mint한다.")
    @PostMapping("/charge")
    public ResponseEntity<ApiResponse<ChargeResponse>> charge(@RequestBody ChargeRequest request) {
        // 1. 충전 처리
        ChargeResponse response = transactionCommandService.charge(request);

        // 2. 성공 응답 반환
        return ResponseEntity.status(TransactionSuccessCode.TRANSACTION_CHARGE_OK.getStatus())
                .body(
                        ApiResponse.onSuccess(
                                TransactionSuccessCode.TRANSACTION_CHARGE_OK, response));
    }

    @Operation(summary = "환전", description = "한강페이 토큰을 burn하고 은행 계좌로 환전한다.")
    @PostMapping("/exchange")
    public ResponseEntity<ApiResponse<ExchangeResponse>> exchange(
            @RequestBody ExchangeRequest request) {
        // 1. 환전 처리
        ExchangeResponse response = transactionCommandService.exchange(request);

        // 2. 성공 응답 반환
        return ResponseEntity.status(TransactionSuccessCode.TRANSACTION_EXCHANGE_OK.getStatus())
                .body(
                        ApiResponse.onSuccess(
                                TransactionSuccessCode.TRANSACTION_EXCHANGE_OK, response));
    }

    @Operation(summary = "결제", description = "지갑 → 지갑 토큰 transfer.")
    @PostMapping("/payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> payment(
            @RequestBody PaymentRequest request) {
        // 1. 결제 처리
        PaymentResponse response = transactionCommandService.payment(request);

        // 2. 성공 응답 반환
        return ResponseEntity.status(TransactionSuccessCode.TRANSACTION_PAYMENT_OK.getStatus())
                .body(
                        ApiResponse.onSuccess(
                                TransactionSuccessCode.TRANSACTION_PAYMENT_OK, response));
    }

    @Operation(summary = "결제 취소", description = "결제의 역방향 transfer로 취소를 기록한다.")
    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<CancelResponse>> cancel(@RequestBody CancelRequest request) {
        // 1. 결제 취소 처리
        CancelResponse response = transactionCommandService.cancel(request);

        // 2. 성공 응답 반환
        return ResponseEntity.status(TransactionSuccessCode.TRANSACTION_CANCEL_OK.getStatus())
                .body(
                        ApiResponse.onSuccess(
                                TransactionSuccessCode.TRANSACTION_CANCEL_OK, response));
    }
}
