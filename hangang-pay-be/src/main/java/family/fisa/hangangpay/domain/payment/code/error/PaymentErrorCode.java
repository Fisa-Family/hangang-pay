package family.fisa.hangangpay.domain.payment.code.error;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PaymentErrorCode implements BaseErrorCode {
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제 내역을 찾을 수 없습니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
