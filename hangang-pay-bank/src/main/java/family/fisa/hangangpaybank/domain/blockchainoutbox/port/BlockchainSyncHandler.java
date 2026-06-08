package family.fisa.hangangpaybank.domain.blockchainoutbox.port;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;

public interface BlockchainSyncHandler {
    BlockchainSyncType type();

    void handle(BlockchainSyncMessage message);
}
