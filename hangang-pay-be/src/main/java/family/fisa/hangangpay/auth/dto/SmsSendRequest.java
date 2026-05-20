package family.fisa.hangangpay.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** SMS 인증 발송 요청 DTO */
@Getter
@NoArgsConstructor
public class SmsSendRequest {

    /** 수신 휴대폰 번호 */
    @NotBlank private String phoneNumber;
}
