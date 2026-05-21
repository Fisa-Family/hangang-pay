package family.fisa.hangangpaybank.global.code.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum GeneralErrorCode implements BaseErrorCode {
    COMMON_BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_BAD_REQUEST", "잘못된 요청입니다."),
    COMMON_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_UNAUTHORIZED", "인증이 필요합니다."),
    COMMON_FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_FORBIDDEN", "접근이 금지되었습니다."),
    COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_NOT_FOUND", "요청한 자원을 찾을 수 없습니다."),
    COMMON_INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
