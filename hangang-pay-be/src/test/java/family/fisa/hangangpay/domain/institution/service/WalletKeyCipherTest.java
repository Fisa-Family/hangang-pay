package family.fisa.hangangpay.domain.institution.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;

class WalletKeyCipherTest {

    private static final String PRIVATE_KEY =
            "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
    private static final String WALLET_ADDRESS = "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73";

    private final WalletKeyCipher walletKeyCipher = new WalletKeyCipher("test-wallet-key-secret");

    @Test
    @DisplayName("개인키를 암호화해 저장하고 복호화해 Credentials를 생성한다")
    void encryptPrivateKeyAndDecryptCredentials() {
        String encryptedPrivateKey = walletKeyCipher.encryptPrivateKey(PRIVATE_KEY);

        assertThat(encryptedPrivateKey).startsWith("v1:");
        assertThat(encryptedPrivateKey).doesNotContain(PRIVATE_KEY);

        Credentials credentials = walletKeyCipher.decryptCredentials(encryptedPrivateKey);

        assertThat(credentials.getAddress()).isEqualToIgnoringCase(WALLET_ADDRESS);
    }

    @Test
    @DisplayName("기존 평문 개인키도 Credentials로 변환할 수 있다")
    void decryptLegacyPlainPrivateKey() {
        Credentials credentials = walletKeyCipher.decryptCredentials(PRIVATE_KEY);

        assertThat(credentials.getAddress()).isEqualToIgnoringCase(WALLET_ADDRESS);
    }
}
