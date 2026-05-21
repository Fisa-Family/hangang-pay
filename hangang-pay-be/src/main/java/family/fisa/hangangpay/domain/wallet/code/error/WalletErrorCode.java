package family.fisa.hangangpay.domain.wallet.code.error;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum WalletErrorCode implements BaseErrorCode {
    WALLET_CREATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "WALLET_CREATION_FAILED", "지갑 생성에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
