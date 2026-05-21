package family.fisa.hangangpaybank.global.exception;

import family.fisa.hangangpaybank.global.code.error.BaseErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final BaseErrorCode code;

    public BusinessException(BaseErrorCode code) {
        super(code.getMessage());
        this.code = code;
    }
}
