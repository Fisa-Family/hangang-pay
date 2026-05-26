package family.fisa.hangangpay.domain.wallet.code.error;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum WalletErrorCode implements BaseErrorCode {
    WALLET_CREATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "WALLET_CREATION_FAILED", "지갑 생성에 실패했습니다."),

    WALLET_KEY_ENCRYPTION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "WALLET_KEY_ENCRYPTION_FAILED",
            "지갑 개인키 암호화에 실패했습니다."),

    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "지갑을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
