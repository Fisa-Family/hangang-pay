package family.fisa.hangangpay.domain.transaction.code;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TransactionErrorCode implements BaseErrorCode {
    INVALID_UNIT(HttpStatus.BAD_REQUEST, "INVALID_UNIT", "만원 단위로 입력해주세요."),
    INVALID_PAYMENT_STATUS(
            HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_STATUS", "결제 요청 상태가 올바르지 않습니다."),
    LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "LIMIT_EXCEEDED", "이번 달 충전 한도를 초과했습니다."),
    CHARGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CHARGE_NOT_FOUND", "충전 내역을 찾을 수 없습니다."),
    EXCHANGE_NOT_FOUND(HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND", "환전 내역을 찾을 수 없습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제 내역을 찾을 수 없습니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "이미 처리된 결제 요청과 다른 요청입니다."),
    PAYMENT_ALREADY_PROCESSING(
            HttpStatus.CONFLICT, "PAYMENT_ALREADY_PROCESSING", "이미 처리 중인 결제 요청입니다."),
    PAYMENT_IDEMPOTENCY_RECORD_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "PAYMENT_IDEMPOTENCY_RECORD_NOT_FOUND",
            "결제 멱등성 기록을 찾을 수 없습니다."),
    PAYMENT_IDEMPOTENCY_RECORD_INVALID(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "PAYMENT_IDEMPOTENCY_RECORD_INVALID",
            "결제 멱등성 기록이 올바르지 않습니다."),
    PAYMENT_RECOVERY_RESULT_INVALID(
            HttpStatus.BAD_GATEWAY, "PAYMENT_RECOVERY_RESULT_INVALID", "은행 결제 조회 결과가 올바르지 않습니다."),
    PAYMENT_NOT_RECOVERABLE(
            HttpStatus.BAD_REQUEST, "PAYMENT_NOT_RECOVERABLE", "복구할 수 없는 결제 상태입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
