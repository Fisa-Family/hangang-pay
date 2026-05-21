package family.fisa.hangangpaybank.domain.blockchain.code.error;

import family.fisa.hangangpaybank.global.code.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BlockchainErrorCode implements BaseErrorCode {
    BLOCKCHAIN_LEDGER_NOT_FOUND(
            HttpStatus.NOT_FOUND, "BLOCKCHAIN_LEDGER_NOT_FOUND", "블록체인 거래를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
