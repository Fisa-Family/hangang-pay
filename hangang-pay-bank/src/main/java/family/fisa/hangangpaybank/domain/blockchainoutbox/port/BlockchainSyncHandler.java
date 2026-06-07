package family.fisa.hangangpaybank.domain.blockchainoutbox.port;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncMessage;
import family.fisa.hangangpaybank.domain.blockchainoutbox.entity.BlockchainSyncType;

/** Sync type(payment, cancel, charge, exchange)에 따른 각각의 핸들러를 정의한다. */
public interface BlockchainSyncHandler {
    BlockchainSyncType type();

    void handle(BlockchainSyncMessage message);
}
