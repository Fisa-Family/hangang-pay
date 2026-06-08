package family.fisa.hangangpaybank.domain.blockchain.service;

import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

@Component
public class BlockchainTransactionKeyConverter {
    public byte[] toBytes32(String uuid) {
        String hex = uuid.replace("-", "");
        byte[] uuidBytes = Numeric.hexStringToByteArray(hex);
        byte[] result = new byte[32];
        System.arraycopy(uuidBytes, 0, result, 16, 16);
        return result;
    }
}
