package family.fisa.hangangpay.domain.transaction.code;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TransactionErrorCode implements BaseErrorCode {
    INVALID_UNIT(HttpStatus.BAD_REQUEST, "INVALID_UNIT", "만원 단위로 입력해주세요."),
    LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "LIMIT_EXCEEDED", "이번 달 충전 한도를 초과했습니다."),
    CHARGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CHARGE_NOT_FOUND", "충전 내역을 찾을 수 없습니다."),
    EXCHANGE_NOT_FOUND(HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND", "환전 내역을 찾을 수 없습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제 내역을 찾을 수 없습니다."),
    EXCHANGE_NOT_ELIGIBLE(
            HttpStatus.BAD_REQUEST,
            "EXCHANGE_NOT_ELIGIBLE",
            "마지막 충전 직후 잔액의 60% 이상을 사용한 후 환전할 수 있습니다."),
    EXCHANGE_IN_PROGRESS(HttpStatus.CONFLICT, "EXCHANGE_IN_PROGRESS", "이미 진행 중인 환전이 있습니다."),
    EXCHANGE_ALREADY_FAILED(
            HttpStatus.CONFLICT, "EXCHANGE_ALREADY_FAILED", "이미 실패한 환전입니다. 새로 시도해 주세요."),
    INVALID_PAYMENT_PIN(HttpStatus.UNAUTHORIZED, "INVALID_PAYMENT_PIN", "결제 비밀번호가 일치하지 않습니다."),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "지갑을 찾을 수 없습니다."),
    EXCHANGE_CONTRACT_FAILED(HttpStatus.BAD_GATEWAY, "EXCHANGE_CONTRACT_FAILED", "환전 컨트렉트 호출에 실패했습니다."),
    BLOCKCHAIN_LEDGER_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "BLOCKCHAIN_LEDGER_NOT_FOUND", "블록체인 ledger를 찾을 수 없습니다."),


    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
