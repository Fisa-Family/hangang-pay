package family.fisa.hangangpaybank.domain.blockchainoutbox.port;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;

public interface BlockchainSyncMessagePublisher {
    void publish(BlockchainSyncMessage message);
}
