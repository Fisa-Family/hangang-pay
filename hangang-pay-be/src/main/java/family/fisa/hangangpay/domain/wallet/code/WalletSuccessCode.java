package family.fisa.hangangpay.domain.wallet.code;

import family.fisa.hangangpay.global.code.success.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 지갑 도메인 성공 코드 */
@Getter
@AllArgsConstructor
public enum WalletSuccessCode implements BaseSuccessCode {

    /** 잔액 조회 성공 */
    WALLET_BALANCE_RETRIEVED(HttpStatus.OK, "WALLET_BALANCE_RETRIEVED", "지갑 잔액을 조회했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
