package family.fisa.hangangpay.domain.transaction.controller;

import family.fisa.hangangpay.domain.transaction.code.PaymentSuccessCode;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentCancelRequest;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.UserPaymentHistoryDetail;
import family.fisa.hangangpay.domain.transaction.service.TransactionCommandService;
import family.fisa.hangangpay.domain.transaction.service.TransactionQueryService;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제", description = "결제 API")
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final TransactionCommandService transactionCommandService;
    private final TransactionQueryService transactionQueryService;

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<PaymentResponse>> executePayment(
            @RequestParam Long partyId, @Valid @RequestBody PaymentExecuteRequest request) {
        PaymentResponse response = transactionCommandService.payment(partyId, request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(PaymentSuccessCode.PAYMENT_EXECUTED, response));
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<PaymentCancelResponse>> cancelPayment(
            @RequestParam Long partyId, @Valid @RequestBody PaymentCancelRequest request) {
        PaymentCancelResponse response = transactionCommandService.cancelPayment(partyId, request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(PaymentSuccessCode.PAYMENT_CANCELLED, response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CursorPageResponse<PaymentHistoryItem>>>
            getUserPaymentHistory(
                    @RequestParam Long partyId,
                    CursorPageRequest cursor,
                    @RequestParam(defaultValue = "20") int size) {
        CursorPageResponse<PaymentHistoryItem> response =
                transactionQueryService.getUserPaymentHistory(partyId, cursor, size);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(PaymentSuccessCode.PAYMENT_HISTORY_RETRIEVED, response));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<UserPaymentHistoryDetail>> getUserPaymentHistoryDetail(
            @RequestParam Long partyId, @PathVariable Long transactionId) {
        UserPaymentHistoryDetail response =
                transactionQueryService.getUserPaymentHistoryDetail(partyId, transactionId);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(PaymentSuccessCode.PAYMENT_DETAIL_RETRIEVED, response));
    }
}
