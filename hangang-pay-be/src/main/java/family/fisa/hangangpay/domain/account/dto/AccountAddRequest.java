package family.fisa.hangangpay.domain.account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 계좌 추가 요청 DTO */
@Getter
@NoArgsConstructor
public class AccountAddRequest {

    /** 금융기관 코드 */
    @NotBlank private String institutionCode;

    /** 등록할 계좌번호 */
    @NotBlank private String accountNumber;
}
