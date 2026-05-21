package family.fisa.hangangpay.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 계좌 1원 인증 검증 요청 DTO */
@Getter
@NoArgsConstructor
public class AccountVerifyRequest {

    /** 계좌번호 */
    @NotBlank private String accountNumber;

    /** 기관 식별자 */
    @NotNull private Long institutionId;

    /** 인증 코드 */
    @NotBlank private String code;
}
