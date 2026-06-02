package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * CHARGE PENDING 거래 생성을 짧은 트랜잭션 경계로 격리
 *
 * <p>wallet row 락 보유 시간을 최소화하기 위해 REQUIRES_NEW로 분리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeTransactionWriter {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final PartyRepository partyRepository;

    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.1");

    /** 기존 PENDING 충전이 있으면 재사용, 없으면 신규 생성 후 transactionUuid 반환 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String getOrCreatePending(Long partyId) {
        // 지갑 행 락 — 동일 사용자의 동시 init 요청 직렬화
        Wallet wallet =
                walletRepository
                        .findByParty_IdForUpdate(partyId)
                        .orElseThrow(
                                () -> new BusinessException(TransactionErrorCode.WALLET_NOT_FOUND));

        // 기존 대기 거래 조회
        Optional<Transaction> existing = transactionRepository.findLatestPendingCharge(partyId);
        if (existing.isPresent()) {
            log.info(
                    "기존 대기 충전 재사용. partyId={}, transactionUuid={}",
                    partyId,
                    existing.get().getTransactionUuid());
            return existing.get().getTransactionUuid();
        }

        Party party =
                partyRepository
                        .findById(partyId)
                        .orElseThrow(
                                () -> new BusinessException(GeneralErrorCode.COMMON_NOT_FOUND));

        // 대기 거래 저장
        Transaction pending = Transaction.chargeInit(party, wallet, DISCOUNT_RATE);
        transactionRepository.save(pending);
        log.info("대기 충전 생성. partyId={}, transactionUuid={}", partyId, pending.getTransactionUuid());
        return pending.getTransactionUuid();
    }
}
