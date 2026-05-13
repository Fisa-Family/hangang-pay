package family.fisa.hangangpay.global.exception;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final BaseErrorCode code;

    public BusinessException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode;
    }
}
