package family.fisa.hangangpaybank.global.code.success;

import org.springframework.http.HttpStatus;

public interface BaseSuccessCode {
    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
