package family.fisa.hangangpaybank.domain.blockchain.dto.request;

import java.math.BigDecimal;

/** 로컬 시드 데이터 온체인 동기화용 mint 요청 */
public record LocalMintRequest(Long institutionId, String walletAddress, BigDecimal amount) {}
