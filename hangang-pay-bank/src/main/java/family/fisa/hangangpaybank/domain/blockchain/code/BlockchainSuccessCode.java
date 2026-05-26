package family.fisa.hangangpaybank.domain.blockchain.code;

import family.fisa.hangangpaybank.global.code.success.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum BlockchainSuccessCode implements BaseSuccessCode {
    BLOCKCHAIN_LEDGER_DETAIL_OK(HttpStatus.OK, "BLOCKCHAIN_LEDGER_DETAIL_OK", "블록체인 거래 정보 조회 성공"),
    BLOCKCHAIN_CONTRACTS_DEPLOYED(
            HttpStatus.CREATED, "BLOCKCHAIN_CONTRACTS_DEPLOYED", "컨트랙트를 성공적으로 배포했습니다.");

    private final HttpStatus status;

    private final String code;

    private final String message;
}
