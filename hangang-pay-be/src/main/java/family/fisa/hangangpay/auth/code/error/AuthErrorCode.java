package family.fisa.hangangpay.auth.code.error;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "전화번호 또는 비밀번호가 올바르지 않습니다"),
    VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND", "인증 요청을 찾을 수 없습니다."),
    VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "VERIFICATION_EXPIRED", "인증 시간이 만료됐습니다."),
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "VERIFICATION_CODE_MISMATCH", "인증 코드가 일치하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
