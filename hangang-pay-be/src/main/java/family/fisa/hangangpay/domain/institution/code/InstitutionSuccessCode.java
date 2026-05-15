package family.fisa.hangangpay.domain.institution.code;

import family.fisa.hangangpay.global.code.success.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum InstitutionSuccessCode implements BaseSuccessCode {
    CONTRACTS_DEPLOYED(HttpStatus.CREATED, "CONTRACTS_DEPLOYED", "컨트랙트를 성공적으로 배포했습니다.");

    private final HttpStatus status;

    private final String code;

    private final String message;
}
