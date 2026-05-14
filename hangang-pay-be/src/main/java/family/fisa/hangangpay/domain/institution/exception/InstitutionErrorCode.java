package family.fisa.hangangpay.domain.institution.exception;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InstitutionErrorCode implements BaseErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "INSTITUTION404_0", "기관을 찾을 수 없습니다."),
    CONTRACT_NOT_DEPLOYED(HttpStatus.BAD_REQUEST, "INSTITUTION400_0", "배포된 컨트랙트를 찾을 수 없습니다."),
    MISSING_DEPLOYMENT_INFO(HttpStatus.BAD_REQUEST, "INSTITUTION400_1", "기관 배포 정보가 부족합니다."),
    CONTRACT_ALREADY_DEPLOYED(HttpStatus.CONFLICT, "INSTITUTION409_0", "이미 배포된 컨트랙트가 있습니다."),
    INVALID_WALLET_KEY(
            HttpStatus.INTERNAL_SERVER_ERROR, "INSTITUTION500_0", "기관 지갑 키 정보가 올바르지 않습니다."),
    ARTIFACT_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR, "INSTITUTION500_1", "컨트랙트 artifact를 찾을 수 없습니다."),
    DEPLOYMENT_RECEIPT_MISSING(
            HttpStatus.BAD_GATEWAY, "INSTITUTION502_0", "컨트랙트 배포 결과가 올바르지 않습니다."),
    BLOCKCHAIN_RPC_FAILED(HttpStatus.BAD_GATEWAY, "INSTITUTION502_1", "블록체인 RPC 요청에 실패했습니다."),
    TRANSACTION_REVERTED(HttpStatus.BAD_GATEWAY, "INSTITUTION502_2", "블록체인 트랜잭션이 실패했습니다."),
    RECEIPT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "INSTITUTION504_0", "블록체인 트랜잭션 확인 시간이 초과되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
