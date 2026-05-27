package family.fisa.hangangpay.domain.transaction.internal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class ChargeRequestHashGenerator {

    /** transactionUuid, partyId, accountId, amount 기준 SHA-256 해시 생성 */
    public String generate(
            String transactionUuid, Long partyId, Long accountId, BigDecimal amount) {
        String raw =
                transactionUuid
                        + ":"
                        + partyId
                        + ":"
                        + accountId
                        + ":"
                        + amount.toPlainString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
