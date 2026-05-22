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
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제 내역을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
