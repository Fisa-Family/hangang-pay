package family.fisa.hangangpaybank.domain.transaction.service;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.ledger.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeStatusResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeQueryService {

    private final AccountLedgerRepository accountLedgerRepository;
    private final BlockchainLedgerRepository blockchainLedgerRepository;

    /**
     * 플랫폼의 transactionUuid로 두 ledger 정합성을 확인하고 응답을 만든다
     */
    public ExchangeStatusResponse getStatus(String transactionUuid) {
        log.info("환전 상태 조회 시작. transactionUuid={}", transactionUuid);

        // 1. account ledger조회
        AccountLedger accountLedger = accountLedgerRepository
            .findByIdempotentKey(transactionUuid)
            .orElseThrow(() ->
                new BusinessException(TransactionErrorCode.TRANSACTION_NOT_FOUND));

        // 2. blockchain_ledger 조회
        BlockchainLedger blockchainLedger = blockchainLedgerRepository
            .findByIdempotentKey(transactionUuid)
            .orElseThrow(() ->
                new BusinessException(TransactionErrorCode.TRANSACTION_NOT_FOUND));

        // 3. 두 ledger 모두 있음 -> SUCCESS 응답
        ExchangeStatusResponse response = ExchangeStatusResponse.of(
            transactionUuid,
            accountLedger,
            blockchainLedger);

        log.info(
            "환전 상태 조회 완료. transactionUuid={}, bankTransactionId={}, txHash={}",
            transactionUuid,
            response.bankTransactionId(),
            response.txHash());

        return response;
    }
}
