package family.fisa.hangangpay.client.bank.code;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public class ExternalBankErrorCode implements BaseErrorCode {

    private final HttpStatus status;
    private final String code;
    private final String message;
}
