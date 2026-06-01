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
            HttpStatus.BAD_REQUEST, "PAYMENT_NOT_RECOVERABLE", "복구할 수 없는 결제 상태입니다."),
    PAYMENT_RATE_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "PAYMENT_RATE_LIMIT_EXCEEDED",
            "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_CANCEL_FORBIDDEN(HttpStatus.FORBIDDEN, "PAYMENT_CANCEL_FORBIDDEN", "취소 권한이 없는 결제입니다."),
    PAYMENT_NOT_CANCELLABLE(
            HttpStatus.BAD_REQUEST, "PAYMENT_NOT_CANCELLABLE", "취소할 수 없는 결제 상태입니다."),
    PAYMENT_ALREADY_CANCELLED(HttpStatus.CONFLICT, "PAYMENT_ALREADY_CANCELLED", "이미 취소된 결제입니다."),
    EXCHANGE_NOT_ELIGIBLE(
            HttpStatus.BAD_REQUEST,
            "EXCHANGE_NOT_ELIGIBLE",
            "마지막 충전 직후 잔액의 60% 이상을 사용한 후 환전할 수 있습니다."),
    EXCHANGE_IN_PROGRESS(HttpStatus.CONFLICT, "EXCHANGE_IN_PROGRESS", "이미 진행 중인 환전이 있습니다."),
    EXCHANGE_ALREADY_FAILED(
            HttpStatus.CONFLICT, "EXCHANGE_ALREADY_FAILED", "이미 실패한 환전입니다. 새로 시도해 주세요."),
    INVALID_PAYMENT_PIN(HttpStatus.UNAUTHORIZED, "INVALID_PAYMENT_PIN", "결제 비밀번호가 일치하지 않습니다."),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "지갑을 찾을 수 없습니다."),
    CANCEL_ALREADY_PROCESSING(
            HttpStatus.CONFLICT, "CANCEL_ALREADY_PROCESSING", "이미 처리 중인 취소 요청입니다."),
    CANCEL_IDEMPOTENCY_RECORD_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "CANCEL_IDEMPOTENCY_RECORD_NOT_FOUND",
            "취소 멱등성 기록을 찾을 수 없습니다."),
    CANCEL_IDEMPOTENCY_RECORD_INVALID(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "CANCEL_IDEMPOTENCY_RECORD_INVALID",
            "취소 멱등성 기록이 올바르지 않습니다."),
    CANCEL_NOT_RECOVERABLE(HttpStatus.BAD_REQUEST, "CANCEL_NOT_RECOVERABLE", "복구할 수 없는 취소 상태입니다."),
    EXCHANGE_CONTRACT_FAILED(
            HttpStatus.BAD_GATEWAY, "EXCHANGE_CONTRACT_FAILED", "환전 컨트렉트 호출에 실패했습니다."),
    BLOCKCHAIN_LEDGER_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "BLOCKCHAIN_LEDGER_NOT_FOUND",
            "블록체인 ledger를 찾을 수 없습니다."),
    CHARGE_ALREADY_PROCESSING(
            HttpStatus.CONFLICT, "CHARGE_ALREADY_PROCESSING", "이미 처리 중인 충전 요청입니다."),
    CHARGE_IDEMPOTENCY_RECORD_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "CHARGE_IDEMPOTENCY_RECORD_NOT_FOUND",
            "충전 멱등성 기록을 찾을 수 없습니다."),
    CHARGE_IDEMPOTENCY_RECORD_INVALID(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "CHARGE_IDEMPOTENCY_RECORD_INVALID",
            "충전 멱등성 기록이 올바르지 않습니다."),
    BLOCKCHAIN_UNAUTHORIZED(HttpStatus.FORBIDDEN, "BLOCKCHAIN_UNAUTHORIZED", "컨트랙트 호출 권한이 없습니다."),
    BLOCKCHAIN_INVALID_ADDRESS(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_INVALID_ADDRESS", "유효하지 않은 주소입니다."),
    BLOCKCHAIN_INVALID_AMOUNT(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_INVALID_AMOUNT", "유효하지 않은 금액입니다."),
    BLOCKCHAIN_INVALID_INSTITUTION_ID(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_INVALID_INSTITUTION_ID", "유효하지 않은 기관 ID입니다."),
    BLOCKCHAIN_MERCHANT_NOT_REGISTERED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_MERCHANT_NOT_REGISTERED", "등록되지 않은 가맹점입니다."),
    BLOCKCHAIN_INSUFFICIENT_TOKEN_BALANCE(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_INSUFFICIENT_TOKEN_BALANCE", "토큰 잔액이 부족합니다."),
    BLOCKCHAIN_ISSUANCE_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_ISSUANCE_LIMIT_EXCEEDED", "지역화폐 발행 한도를 초과했습니다."),
    BLOCKCHAIN_INSUFFICIENT_RESERVE(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_INSUFFICIENT_RESERVE", "지급준비금 잔액이 부족합니다."),
    BLOCKCHAIN_RESERVE_EXCEEDS_LOCKED_CBDC(
            HttpStatus.BAD_REQUEST,
            "BLOCKCHAIN_RESERVE_EXCEEDS_LOCKED_CBDC",
            "지급준비금이 예치된 CBDC를 초과합니다."),
    BLOCKCHAIN_RESERVE_MOVE_FAILED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_RESERVE_MOVE_FAILED", "지급준비금 이동에 실패했습니다."),
    BLOCKCHAIN_DEPOSIT_TOKEN_MINT_FAILED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_DEPOSIT_TOKEN_MINT_FAILED", "예금토큰 발행에 실패했습니다."),
    BLOCKCHAIN_DEPOSIT_TOKEN_BURN_FAILED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_DEPOSIT_TOKEN_BURN_FAILED", "예금토큰 소각에 실패했습니다."),
    BLOCKCHAIN_TRANSFER_FAILED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_TRANSFER_FAILED", "토큰 이체에 실패했습니다."),
    BLOCKCHAIN_BANK_NOT_REGISTERED(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_BANK_NOT_REGISTERED", "등록되지 않은 은행입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
