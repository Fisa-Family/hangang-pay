package family.fisa.hangangpaybank.global.code.error;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode {
    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
