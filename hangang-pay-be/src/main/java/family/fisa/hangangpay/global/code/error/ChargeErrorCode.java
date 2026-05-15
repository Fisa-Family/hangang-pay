package family.fisa.hangangpay.global.code.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 충전 도메인 에러 코드 */
@AllArgsConstructor
@Getter
public enum ChargeErrorCode implements BaseErrorCode {

    /** 만원 단위가 아닌 금액 입력 */
    INVALID_UNIT(HttpStatus.BAD_REQUEST, "INVALID_UNIT", "만원 단위로 입력해주세요."),

    /** 월 충전 한도 초과 */
    LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "LIMIT_EXCEEDED", "이번 달 충전 한도를 초과했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
