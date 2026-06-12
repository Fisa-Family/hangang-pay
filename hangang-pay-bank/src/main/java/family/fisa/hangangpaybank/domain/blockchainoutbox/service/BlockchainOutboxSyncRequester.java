package family.fisa.hangangpaybank.domain.blockchainoutbox.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import family.fisa.hangangpaybank.domain.blockchain.code.error.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequest;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequestResult;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutbox;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainOutboxStatus;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncRequester;
import family.fisa.hangangpaybank.domain.blockchainoutbox.repository.BlockchainOutboxRepository;
import family.fisa.hangangpaybank.domain.institution.entity.ContractType;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.ContractRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import family.fisa.hangangpaybank.global.fault.FaultHookService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BlockchainOutboxSyncRequester implements BlockchainSyncRequester {

    private final BlockchainLedgerRepository blockchainLedgerRepository;
    private final BlockchainOutboxRepository blockchainOutboxRepository;
    private final ContractRepository contractRepository;
    private final ObjectMapper objectMapper;
    private final BlockchainOrderingSequenceAllocator sequenceAllocator;
    private final FaultHookService faultHook;

    /** 블록체인으로 요청을 전송하기 위한 준비 작업. 블록체인 원장과 outbox에 요청 기록을 남긴다. */
    @Override
    public BlockchainSyncRequestResult request(BlockchainSyncRequest request) {
        // LOCAL_CURRENCY 컨트랙트의 발행 기관을 찾는다.
        Institution institution = findLocalCurrencyOwner();
        String orderingKey = normalizeOrderingKey(request.orderingKey());
        Long seqNo = sequenceAllocator.allocate(orderingKey);

        // blockchain_ledger에 PENDING 상태의 거래를 기록한다.
        BlockchainLedger ledger = blockchainLedgerRepository.save(
                BlockchainLedger.of(
                        institution,
                        BlockchainTxStatus.PENDING,
                        request.transactionUuid()));

        // H2: TX 내부 — halt 시 MySQL이 전체 TX를 롤백, DB 흔적 없음 (atomicity 검증)
        faultHook.hit(FaultHookService.AFTER_BLOCKCHAIN_LEDGER_SAVE);

        // blockchain_outbox에 NEW 상태의 메시지를 저장한다.
        BlockchainOutbox outbox = blockchainOutboxRepository.save(
                BlockchainOutbox.builder()
                        .blockchainLedgerId(ledger.getId())
                        .transactionUuid(request.transactionUuid())
                        .orderingKey(orderingKey)
                        .seqNo(seqNo)
                        .type(request.type())
                        .status(BlockchainOutboxStatus.NEW)
                        .payload(serializePayload(request.payload()))
                        .retryCount(0)
                        .build());

        // H3: TX 커밋 이후 실행 — halt 시 wallet+blockchain_ledger+outbox 모두 커밋된 상태로 프로세스 종료
        // (K-4)
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        faultHook.hit(FaultHookService.AFTER_OUTBOX_SAVE);
                    }
                });

        return new BlockchainSyncRequestResult(ledger.getId(), outbox.getId());
    }

    private Institution findLocalCurrencyOwner() {
        return contractRepository
                .findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY)
                .orElseThrow(
                        () -> new BusinessException(
                                BlockchainErrorCode.BLOCKCHAIN_CONTRACT_NOT_FOUND))
                .getInstitution();
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_RPC_FAILED);
        }
    }

    private static String normalizeOrderingKey(String orderingKey) {
        if (orderingKey == null || orderingKey.isBlank()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_INVALID_ADDRESS);
        }
        String lower = orderingKey.toLowerCase();
        return lower.startsWith("0x") ? lower : "0x" + lower;
    }
}
