package family.fisa.hangangpay.global.client.dto;

import java.math.BigDecimal;

/** bank 서비스 지갑 조회 응답 데이터 */
public record BankWalletResult(
        Long id, Long institutionId, String walletAddress, BigDecimal balance) {}
