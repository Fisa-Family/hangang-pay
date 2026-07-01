package family.fisa.hangangpay.domain.user.code;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorCode implements BaseErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다"),
    INVALID_PIN_NUMBER(HttpStatus.BAD_REQUEST, "INVALID_PIN_NUMBER", "핀 번호가 일치하지 않습니다."),
    INVALID_HISTORY_TYPE(HttpStatus.BAD_REQUEST, "INVALID_HISTORY_TYPE", "지원하지 않는 내역 타입입니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "NOT_OWNER", "본인의 내역만 조회할 수 있습니다."),
    HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "HISTORY_NOT_FOUND", "내역 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
