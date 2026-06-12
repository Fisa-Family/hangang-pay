package family.fisa.hangangpaybank.domain.blockchainoutbox.dto;

import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;

public interface BlockchainSyncRequest {
    BlockchainSyncType type();

    String transactionUuid();

    String orderingKey();

    Object payload();
}
