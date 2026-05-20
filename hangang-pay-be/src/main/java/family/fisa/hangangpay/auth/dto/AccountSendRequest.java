package family.fisa.hangangpay.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 계좌 1원 인증 발송 요청 DTO */
@Getter
@NoArgsConstructor
public class AccountSendRequest {

    /** 계좌번호 */
    @NotBlank
    private String accountNumber;

    /** 기관 식별자 */
    @NotNull
    private Long institutionId;
}
