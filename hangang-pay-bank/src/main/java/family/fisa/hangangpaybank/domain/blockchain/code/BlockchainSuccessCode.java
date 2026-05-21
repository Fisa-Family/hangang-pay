package family.fisa.hangangpaybank.domain.blockchain.code;

import family.fisa.hangangpaybank.global.code.success.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum BlockchainSuccessCode implements BaseSuccessCode {
    BLOCKCHAIN_LEDGER_DETAIL_OK(HttpStatus.OK, "BLOCKCHAIN_LEDGER_DETAIL_OK", "블록체인 거래 정보 조회 성공");

    private final HttpStatus status;

    private final String code;

    private final String message;
}
