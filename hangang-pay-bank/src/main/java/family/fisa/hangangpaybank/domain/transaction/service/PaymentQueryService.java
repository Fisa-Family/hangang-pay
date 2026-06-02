package family.fisa.hangangpaybank.domain.transaction.service;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentStatusResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final BlockchainLedgerRepository blockchainLedgerRepository;

    /** 플랫폼의 transactionUuid로 결제 상태를 조회한다 */
    public PaymentStatusResponse getStatus(String transactionUuid) {
        log.info("[bank] 결제 상태 조회 시작. transactionUuid={}", transactionUuid);

        // 1. blockchain_ledger 조회 (idempotent_key = transactionUuid)
        BlockchainLedger ledger = getBlockchainLedger(transactionUuid);

        // 2. 상태 매핑 후 응답 반환
        PaymentStatusResponse response = PaymentStatusResponse.of(transactionUuid, ledger);

        log.info(
                "[bank] 결제 상태 조회 완료. transactionUuid={}, status={}, bankTransactionId={}",
                transactionUuid,
                response.status(),
                response.bankTransactionId());

        return response;
    }

    private @NonNull BlockchainLedger getBlockchainLedger(String transactionUuid) {
        return blockchainLedgerRepository
                .findByIdempotentKey(transactionUuid)
                .orElseThrow(
                        () -> new BusinessException(TransactionErrorCode.TRANSACTION_NOT_FOUND));
    }
}
