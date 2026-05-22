package family.fisa.hangangpay.global.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** bank 서비스 공통 응답 래퍼 */
@Getter
@NoArgsConstructor
public class BankApiResponse<T> {

    @JsonProperty("isSuccess")
    private boolean success;

    private int status;
    private String code;
    private String message;

    /** 실제 응답 데이터 */
    private T result;
}
