package family.fisa.hangangpay.domain.account.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** 계좌 목록 조회 응답 래퍼 */
@Getter
@Builder
public class AccountListResponse {

    /** 계좌 항목 목록 */
    private List<AccountResponse> accounts;

    /** 전체 계좌 수 */
    private int totalCount;
}
