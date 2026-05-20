package family.fisa.hangangpay.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** SMS 인증 검증 요청 DTO */
@Getter
@NoArgsConstructor
public class SmsVerifyRequest {

    /** 수신 휴대폰 번호 */
    @NotBlank private String phoneNumber;

    /** 인증 코드 */
    @NotBlank private String code;
}
