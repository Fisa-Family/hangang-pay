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

/** 환전 상태 조회. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeQueryService {

    private final AccountLedgerRepository accountLedgerRepository;
    private final BlockchainLedgerRepository blockchainLedgerRepository;

    public ExchangeStatusResponse getStatus(String transactionUuid) {
        log.info("환전 상태 조회 시작. transactionUuid={}", transactionUuid);

        // bankTransactionId 제공용
        AccountLedger accountLedger =
                accountLedgerRepository
                        .findByIdempotentKey(transactionUuid)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.TRANSACTION_NOT_FOUND));

        // 진행 상태 판정
        BlockchainLedger blockchainLedger =
                blockchainLedgerRepository
                        .findByIdempotentKey(transactionUuid)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                TransactionErrorCode.TRANSACTION_NOT_FOUND));

        ExchangeStatusResponse response =
                ExchangeStatusResponse.of(transactionUuid, accountLedger, blockchainLedger);

        log.info(
                "환전 상태 조회 완료. transactionUuid={}, status={}, txHash={}",
                transactionUuid,
                response.status(),
                response.txHash());
        return response;
    }
}
