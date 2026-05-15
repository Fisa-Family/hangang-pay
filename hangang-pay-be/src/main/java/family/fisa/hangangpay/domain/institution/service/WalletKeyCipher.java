package family.fisa.hangangpay.domain.institution.service;

import family.fisa.hangangpay.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;

@Component
public class WalletKeyCipher {

    public Credentials decryptCredentials(String encryptedPrivateKey) {
        if (encryptedPrivateKey == null || encryptedPrivateKey.isBlank()) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_INVALID_WALLET_KEY);
        }

        try {
            return Credentials.create(stripHexPrefix(encryptedPrivateKey.trim()));
        } catch (RuntimeException e) {
            throw new BusinessException(InstitutionErrorCode.INSTITUTION_INVALID_WALLET_KEY);
        }
    }

    private static String stripHexPrefix(String value) {
        if (value.startsWith("0x") || value.startsWith("0X")) {
            return value.substring(2);
        }

        return value;
    }
}
