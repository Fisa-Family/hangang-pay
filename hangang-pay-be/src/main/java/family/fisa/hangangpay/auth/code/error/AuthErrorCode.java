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
    VERIFICATION_CODE_MISMATCH(
            HttpStatus.BAD_REQUEST, "VERIFICATION_CODE_MISMATCH", "인증 코드가 일치하지 않습니다."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "TERMS_NOT_AGREED", "필수 약관에 모두 동의해주세요."),
    SIGNUP_PHONE_NOT_VERIFIED(HttpStatus.FORBIDDEN, "SIGNUP_PHONE_NOT_VERIFIED", "휴대폰 인증이 필요합니다."),
    SIGNUP_PHONE_MISMATCH(
            HttpStatus.FORBIDDEN, "SIGNUP_PHONE_MISMATCH", "인증한 휴대폰 번호와 요청 정보가 일치하지 않습니다."),
    SIGNUP_PHONE_VERIFICATION_EXPIRED(
            HttpStatus.FORBIDDEN, "SIGNUP_PHONE_VERIFICATION_EXPIRED", "휴대폰 인증이 만료되었습니다."),
    SIGNUP_ACCOUNT_NOT_VERIFIED(
            HttpStatus.FORBIDDEN, "SIGNUP_ACCOUNT_NOT_VERIFIED", "계좌 인증이 필요합니다."),
    SIGNUP_ACCOUNT_MISMATCH(
            HttpStatus.FORBIDDEN, "SIGNUP_ACCOUNT_MISMATCH", "인증한 계좌와 요청 정보가 일치하지 않습니다."),
    SIGNUP_ACCOUNT_VERIFICATION_EXPIRED(
            HttpStatus.FORBIDDEN, "SIGNUP_ACCOUNT_VERIFICATION_EXPIRED", "계좌 인증이 만료되었습니다."),
    DUPLICATE_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "DUPLICATE_PHONE_NUMBER", "이미 가입된 휴대폰 번호입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
