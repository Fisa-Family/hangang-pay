package family.fisa.hangangpay.domain.wallet.service;

import family.fisa.hangangpay.domain.wallet.dto.WalletBalanceResponse;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.client.BankWalletClient;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지갑 조회 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletQueryService {

    private static final String UNIT = "KRW";

    /** 지갑 리포지토리 */
    private final WalletRepository walletRepository;

    /** bank 서비스 지갑 클라이언트 */
    private final BankWalletClient bankWalletClient;

    /** 파티 식별자 기준 지갑 잔액 조회 */
    public WalletBalanceResponse getBalance(Long partyId) {
        // 파티 식별자로 지갑 조회
        Wallet wallet =
                walletRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(
                                () -> new BusinessException(GeneralErrorCode.COMMON_NOT_FOUND));

        // bank 서비스에서 예금 토큰 잔액 조회
        BigDecimal balance = bankWalletClient.getBalance(wallet.getAddress());

        log.info(
                "지갑 잔액 조회: partyId={}, walletAddress={}, balance={}",
                partyId,
                wallet.getAddress(),
                balance);

        return WalletBalanceResponse.builder()
                .walletAddress(wallet.getAddress())
                .balance(balance)
                .unit(UNIT)
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
}
