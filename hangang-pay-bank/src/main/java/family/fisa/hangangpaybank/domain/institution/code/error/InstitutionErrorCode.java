package family.fisa.hangangpaybank.domain.institution.code.error;

import family.fisa.hangangpaybank.global.code.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InstitutionErrorCode implements BaseErrorCode {
    INSTITUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "INSTITUTION_NOT_FOUND", "기관을 찾을 수 없습니다."),
    INSTITUTION_INVALID_WALLET_KEY(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INSTITUTION_INVALID_WALLET_KEY",
            "기관 지갑 키 정보가 올바르지 않습니다."),
    INSTITUTION_ARTIFACT_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INSTITUTION_ARTIFACT_NOT_FOUND",
            "컨트랙트 artifact를 찾을 수 없습니다."),

    INSTITUTION_DEPLOYMENT_RECEIPT_MISSING(
            HttpStatus.BAD_GATEWAY,
            "INSTITUTION_DEPLOYMENT_RECEIPT_MISSING",
            "컨트랙트 배포 결과가 올바르지 않습니다."),
    INSTITUTION_CONTRACT_ALREADY_DEPLOYED(
            HttpStatus.CONFLICT, "INSTITUTION_CONTRACT_ALREADY_DEPLOYED", "이미 배포된 컨트랙트가 있습니다."),
    INSTITUTION_CONTRACT_NOT_DEPLOYED(
            HttpStatus.BAD_REQUEST, "INSTITUTION_CONTRACT_NOT_DEPLOYED", "배포된 컨트랙트를 찾을 수 없습니다."),
    INSTITUTION_MISSING_DEPLOYMENT_INFO(
            HttpStatus.BAD_REQUEST, "INSTITUTION_MISSING_DEPLOYMENT_INFO", "기관 배포 정보가 부족합니다."),
    BANK_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "BANK_ACCOUNT_NOT_FOUND", "계좌를 찾을 수 없습니다."),
    BANK_WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "BANK_WALLET_NOT_FOUND", "지갑을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
