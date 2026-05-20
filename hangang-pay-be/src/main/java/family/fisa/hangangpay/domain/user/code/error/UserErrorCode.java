package family.fisa.hangangpay.domain.user.code.error;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorCode implements BaseErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다"),
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
    DUPLICATE_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "DUPLICATE_PHONE_NUMBER", "이미 가입된 휴대폰 번호입니다."),
    INSTITUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "INSTITUTION_NOT_FOUND", "금융기관을 찾을 수 없습니다."),
    WALLET_CREATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "WALLET_CREATION_FAILED", "지갑 생성에 실패했습니다."),
    INVALID_HISTORY_TYPE(HttpStatus.BAD_REQUEST, "INVALID_HISTORY_TYPE", "지원하지 않는 내역 타입입니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "NOT_OWNER", "본인의 내역만 조회할 수 있습니다."),
    HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "HISTORY_NOT_FOUND", "내역 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
