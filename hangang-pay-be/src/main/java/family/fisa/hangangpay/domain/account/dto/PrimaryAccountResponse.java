package family.fisa.hangangpay.domain.account.dto;

import lombok.Builder;
import lombok.Getter;

/** 주거래 계좌 변경 응답 DTO */
@Getter
@Builder
public class PrimaryAccountResponse {

    /** 새로 지정된 주거래 계좌 식별자 */
    private Long accountId;

    /** 계좌 유형 - 항상 PRIMARY */
    private String accountType;

    /** 변경 전 주거래 계좌 식별자, 이미 주거래인 경우 null */
    private Long previousPrimaryAccountId;
}
