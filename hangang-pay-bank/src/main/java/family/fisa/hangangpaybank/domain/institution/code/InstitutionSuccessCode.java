package family.fisa.hangangpaybank.domain.institution.code;

import family.fisa.hangangpaybank.global.code.success.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum InstitutionSuccessCode implements BaseSuccessCode {
    CONTRACTS_DEPLOYED(HttpStatus.CREATED, "CONTRACTS_DEPLOYED", "컨트랙트를 성공적으로 배포했습니다."),
    INSTITUTION_LIST_OK(HttpStatus.OK, "INSTITUTION_LIST_OK", "기관 목록 조회 성공"),
    INSTITUTION_DETAIL_OK(HttpStatus.OK, "INSTITUTION_DETAIL_OK", "기관 정보 조회 성공"),
    BANK_ACCOUNT_CREATED(HttpStatus.OK, "BANK_ACCOUNT_CREATED", "계좌가 생성되었습니다."),
    BANK_ACCOUNT_DETAIL_OK(HttpStatus.OK, "BANK_ACCOUNT_DETAIL_OK", "계좌 정보 조회 성공"),
    BANK_WALLET_CREATED(HttpStatus.OK, "BANK_WALLET_CREATED", "지갑이 생성되었습니다."),
    BANK_WALLET_DETAIL_OK(HttpStatus.OK, "BANK_WALLET_DETAIL_OK", "지갑 정보 조회 성공");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
