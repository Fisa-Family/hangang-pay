package family.fisa.hangangpay.domain.transfer.code.error;

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

    /** 외부 결제 API 실패 */
    public static final ChargeErrorCode PAYMENT_FAILED =
            new ChargeErrorCode(HttpStatus.BAD_REQUEST, "PAYMENT_FAILED", "결제에 실패했습니다. 다시 시도해주세요.");

    /** 블록체인 mint 실패 */
    public static final ChargeErrorCode MINT_FAILED =
            new ChargeErrorCode(HttpStatus.BAD_REQUEST, "MINT_FAILED", "충전 처리에 실패했습니다. 결제는 취소됩니다.");

    /** 계좌 잔액 부족 */
    public static final ChargeErrorCode INSUFFICIENT_BALANCE =
            new ChargeErrorCode(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", "계좌 잔액이 부족합니다.");

    /** 발행 가능량 초과 */
    public static final ChargeErrorCode ISSUABLE_EXCEEDED =
            new ChargeErrorCode(HttpStatus.BAD_REQUEST, "ISSUABLE_EXCEEDED", "은행의 발행 가능량을 초과했습니다.");

    /** 에러 코드 문자열로 인스턴스 반환 */
    public static ChargeErrorCode from(String code) {
        return switch (code) {
            case "INVALID_UNIT" -> INVALID_UNIT;
            case "LIMIT_EXCEEDED" -> LIMIT_EXCEEDED;
            case "PAYMENT_FAILED" -> PAYMENT_FAILED;
            case "MINT_FAILED" -> MINT_FAILED;
            case "INSUFFICIENT_BALANCE" -> INSUFFICIENT_BALANCE;
            case "ISSUABLE_EXCEEDED" -> ISSUABLE_EXCEEDED;
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
