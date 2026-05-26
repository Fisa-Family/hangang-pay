package family.fisa.hangangpay.domain.transaction.controller;

import family.fisa.hangangpay.domain.transaction.code.TransactionSuccessCode;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.request.PaymentIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentExecutionResponse;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentIntentResponse;
import family.fisa.hangangpay.domain.transaction.service.TransactionCommandService;
import family.fisa.hangangpay.global.response.ApiResponse;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@Tag(name = "결제", description = "결제 API")
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final TransactionCommandService transactionCommandService;

    @PostMapping("/intents")
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> createPaymentIntent(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @Valid @RequestBody PaymentIntentCreateRequest request) {
        PaymentIntentResponse response =
                transactionCommandService.createPaymentIntent(partyId, request);

        return ResponseEntity.status(TransactionSuccessCode.PAYMENT_INTENT_CREATED.getStatus())
                .body(
                        ApiResponse.onSuccess(
                                TransactionSuccessCode.PAYMENT_INTENT_CREATED, response));
    }

    @PostMapping("/{transactionUuid}/execute")
    public ResponseEntity<ApiResponse<PaymentExecutionResponse>> executePayment(
            @SessionAttribute(SessionAttributeNames.USER_ID) Long userId,
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @PathVariable String transactionUuid,
            @Valid @RequestBody PaymentExecuteRequest request) {
        PaymentExecutionResponse response =
                transactionCommandService.executePayment(userId, partyId, transactionUuid, request);

        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.PAYMENT_EXECUTED, response));
    }

    /** UNKNOWN 상태 결제를 복구하기 위한 재조회/재동기화 API */
    @PostMapping("/{transactionUuid}/recover")
    public ResponseEntity<ApiResponse<PaymentExecutionResponse>> recoverPayment(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
            @PathVariable String transactionUuid) {
        PaymentExecutionResponse response =
                transactionCommandService.recoverPayment(partyId, transactionUuid);

        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.PAYMENT_RECOVERED, response));
    }
}
