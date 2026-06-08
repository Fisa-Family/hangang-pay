package family.fisa.hangangpaybank.domain.blockchainoutbox.port;

import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequest;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequestResult;

public interface BlockchainSyncRequester {
    BlockchainSyncRequestResult request(BlockchainSyncRequest request);
}
