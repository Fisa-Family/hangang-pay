package family.fisa.hangangpay.domain.transaction.code.error;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 충전 도메인 에러 코드 */
public record ChargeErrorCode(HttpStatus status, String code, String message)
        implements BaseErrorCode {

    /** 만원 단위가 아닌 금액 입력 */
    public static final ChargeErrorCode INVALID_UNIT =
            new ChargeErrorCode(HttpStatus.BAD_REQUEST, "INVALID_UNIT", "만원 단위로 입력해주세요.");

    /** 월 충전 한도 초과 */
    public static final ChargeErrorCode LIMIT_EXCEEDED =
            new ChargeErrorCode(HttpStatus.BAD_REQUEST, "LIMIT_EXCEEDED", "이번 달 충전 한도를 초과했습니다.");

    /** 에러 코드 문자열로 인스턴스 반환 */
    public static ChargeErrorCode from(String code) {
        return switch (code) {
            case "INVALID_UNIT" -> INVALID_UNIT;
            case "LIMIT_EXCEEDED" -> LIMIT_EXCEEDED;
            default -> throw new IllegalArgumentException("알 수 없는 충전 에러 코드: " + code);
        };
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
