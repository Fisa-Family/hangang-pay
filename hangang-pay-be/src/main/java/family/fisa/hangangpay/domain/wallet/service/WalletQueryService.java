package family.fisa.hangangpay.domain.wallet.service;

import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.wallet.dto.WalletBalanceResponse;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
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

    /** 지갑 레포지토리 */
    private final WalletRepository walletRepository;

    /** 거래 내역 레포지토리 */
    private final TransactionRepository transactionRepository;

    /** 파티 식별자 기준 지갑 잔액 조회 */
    public WalletBalanceResponse getBalance(Long partyId) {
        // 파티 식별자로 지갑 조회
        Wallet wallet =
                walletRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(
                                () -> new BusinessException(GeneralErrorCode.COMMON_NOT_FOUND));

        // DB 거래 내역 합산으로 잔액 계산 (충전 - 결제 - 환전)
        BigDecimal charged =
                transactionRepository.sumAllSuccessByType(partyId, TransactionType.CHARGE);
        BigDecimal paid =
                transactionRepository.sumAllSuccessByType(partyId, TransactionType.PAYMENT);
        BigDecimal exchanged =
                transactionRepository.sumAllSuccessByType(partyId, TransactionType.EXCHANGE);
        BigDecimal balance = charged.subtract(paid).subtract(exchanged);

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
