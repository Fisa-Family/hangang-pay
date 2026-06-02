package family.fisa.hangangpay.auth.code.success;

import family.fisa.hangangpay.global.code.success.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum AuthSuccessCode implements BaseSuccessCode {
    USER_REGISTERED(HttpStatus.CREATED, "USER_REGISTERED", "회원가입이 완료되었습니다."),
    MERCHANT_REGISTERED(HttpStatus.CREATED, "MERCHANT_REGISTERED", "가맹점 회원가입이 완료되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
