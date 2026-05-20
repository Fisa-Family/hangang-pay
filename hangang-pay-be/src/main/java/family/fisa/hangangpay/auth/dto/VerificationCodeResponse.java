package family.fisa.hangangpay.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 인증 코드 발송 응답 DTO */
@Getter
@AllArgsConstructor
public class VerificationCodeResponse {

    /** 발급된 인증 코드 */
    private String code;
}
