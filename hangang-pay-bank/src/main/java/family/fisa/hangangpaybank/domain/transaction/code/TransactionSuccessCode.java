package family.fisa.hangangpaybank.domain.transaction.code;

import family.fisa.hangangpaybank.global.code.success.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum TransactionSuccessCode implements BaseSuccessCode {
    TRANSACTION_CHARGE_OK(HttpStatus.OK, "TRANSACTION_CHARGE_OK", "충전이 완료되었습니다."),

    TRANSACTION_EXCHANGE_OK(HttpStatus.OK, "TRANSACTION_EXCHANGE_OK", "환전이 완료되었습니다."),

    TRANSACTION_PAYMENT_OK(HttpStatus.OK, "TRANSACTION_PAYMENT_OK", "결제가 완료되었습니다."),

    TRANSACTION_CANCEL_OK(HttpStatus.OK, "TRANSACTION_CANCEL_OK", "결제 취소가 완료되었습니다.");

    private final HttpStatus status;

    private final String code;

    private final String message;
}
