package family.fisa.hangangpaybank.domain.blockchainoutbox.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BlockchainOutboxSyncRequester implements BlockchainSyncRequester {

    private final BlockchainLedgerRepository blockchainLedgerRepository;
    private final BlockchainOutboxRepository blockchainOutboxRepository;
    private final ContractRepository contractRepository;
    private final ObjectMapper objectMapper;

    @Override
    public BlockchainSyncRequestResult request(BlockchainSyncRequest request) {
        Institution institution = findLocalCurrencyOwner();

        BlockchainLedger ledger =
                blockchainLedgerRepository.save(
                        BlockchainLedger.of(
                                institution,
                                BlockchainTxStatus.PENDING,
                                request.transactionUuid()));

        BlockchainOutbox outbox =
                blockchainOutboxRepository.save(
                        BlockchainOutbox.builder()
                                .blockchainLedgerId(ledger.getId())
                                .transactionUuid(request.transactionUuid())
                                .type(request.type())
                                .status(BlockchainOutboxStatus.NEW)
                                .payload(serializePayload(request.payload()))
                                .retryCount(0)
                                .build());

        return new BlockchainSyncRequestResult(ledger.getId(), outbox.getId());
    }

    private Institution findLocalCurrencyOwner() {
        return contractRepository
                .findFirstByNameOrderByIdAsc(ContractType.LOCAL_CURRENCY)
                .orElseThrow(
                        () ->
                                new BusinessException(
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
}
