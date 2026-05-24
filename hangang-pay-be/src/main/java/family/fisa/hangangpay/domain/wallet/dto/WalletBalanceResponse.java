package family.fisa.hangangpay.domain.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** 지갑 잔액 조회 응답 DTO */
@Getter
@Builder
public class WalletBalanceResponse {

    /** 지갑 주소 */
    private String walletAddress;

    /** 잔액 */
    private BigDecimal balance;

    /** 화폐 단위 */
    private String unit;

    /** 지갑 마지막 업데이트 시각 */
    private LocalDateTime updatedAt;
}
