package family.fisa.hangangpaybank.domain.blockchain.code.error;

import family.fisa.hangangpaybank.global.code.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BlockchainErrorCode implements BaseErrorCode {
    BLOCKCHAIN_RPC_FAILED(HttpStatus.BAD_GATEWAY, "BLOCKCHAIN_RPC_FAILED", "블록체인 RPC 요청에 실패했습니다."),
    BLOCKCHAIN_TRANSACTION_REVERTED(
            HttpStatus.BAD_GATEWAY, "BLOCKCHAIN_TRANSACTION_REVERTED", "블록체인 트랜잭션이 실패했습니다."),
    BLOCKCHAIN_DEPLOYMENT_RECEIPT_MISSING(
            HttpStatus.BAD_GATEWAY,
            "BLOCKCHAIN_DEPLOYMENT_RECEIPT_MISSING",
            "컨트랙트 배포 결과에 컨트랙트 주소가 존재하지 않습니다."),
    BLOCKCHAIN_RECEIPT_TIMEOUT(
            HttpStatus.GATEWAY_TIMEOUT, "BLOCKCHAIN_RECEIPT_TIMEOUT", "블록체인 트랜잭션 확인 시간이 초과되었습니다."),
    BLOCKCHAIN_LEDGER_NOT_FOUND(
            HttpStatus.NOT_FOUND, "BLOCKCHAIN_LEDGER_NOT_FOUND", "블록체인 거래를 찾을 수 없습니다."),
    BLOCKCHAIN_CONTRACT_NOT_FOUND(
            HttpStatus.BAD_REQUEST, "BLOCKCHAIN_CONTRACT_NOT_FOUND", "컨트랙트를 찾을 수 없습니다."),
    BLOCKCHAIN_CONTRACT_DEPLOYMENT_RESULT_INVALID(
            HttpStatus.BAD_REQUEST,
            "BLOCKCHAIN_CONTRACT_DEPLOYMENT_RESULT_INVALID",
            "컨트랙트 배포 결과가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
