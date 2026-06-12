package family.fisa.hangangpaybank.domain.transaction.dto;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequest;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.payload.ExchangeBlockchainPayload;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;

public record ExchangeSyncRequest(String transactionUuid, ExchangeBlockchainPayload data)
        implements BlockchainSyncRequest {
    @Override
    public BlockchainSyncType type() {
        return BlockchainSyncType.EXCHANGE;
    }

    @Override
    public String orderingKey() {
        return data.walletAddress();
    }

    @Override
    public Object payload() {
        return data;
    }
}
