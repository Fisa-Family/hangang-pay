package family.fisa.hangangpay.global.code.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 계좌 도메인 에러 코드 */
@AllArgsConstructor
@Getter
public enum AccountErrorCode implements BaseErrorCode {

    /** 계좌 최대 등록 수 초과 */
    MAX_ACCOUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "MAX_ACCOUNT_EXCEEDED", "계좌는 최대 3개까지 등록 가능합니다."),

    /** 이미 등록된 계좌 */
    DUPLICATE_ACCOUNT(HttpStatus.BAD_REQUEST, "DUPLICATE_ACCOUNT", "이미 등록된 계좌입니다."),

    /** 은행 원장에 존재하지 않는 계좌 */
    BANK_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "BANK_ACCOUNT_NOT_FOUND", "존재하지 않는 계좌입니다."),

    /** 주거래 계좌 삭제 시도 */
    PRIMARY_ACCOUNT_DELETE(HttpStatus.BAD_REQUEST, "PRIMARY_ACCOUNT_DELETE", "주거래 계좌는 삭제할 수 없습니다."),

    /** 마지막 계좌 삭제 시도 */
    LAST_ACCOUNT_DELETE(HttpStatus.BAD_REQUEST, "LAST_ACCOUNT_DELETE", "계좌는 최소 1개 이상 유지해야 합니다."),

    /** 존재하지 않거나 본인 소유가 아닌 계좌 */
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "계좌를 찾을 수 없습니다."),

    /** 계좌번호 형식 오류 */
    INVALID_ACCOUNT_NUMBER(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_NUMBER", "계좌번호 형식이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
