package family.fisa.hangangpay.domain.wallet.service;

import family.fisa.hangangpay.domain.institution.entity.BankWallet;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.service.BankWalletService;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.wallet.code.error.WalletErrorCode;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

@Service
@RequiredArgsConstructor
@Transactional
public class WalletService {

    private static final BigDecimal INITIAL_BANK_WALLET_BALANCE = BigDecimal.ZERO;
    private static final int PRIVATE_KEY_HEX_LENGTH = 64;

    private final WalletRepository walletRepository;
    private final BankWalletService bankWalletService;

    /**
     * 회원/가맹점 가입 시 사용할 EOA 지갑을 생성하고 WALLET, BANK_WALLET 테이블에 저장합니다.
     *
     * @param party 지갑을 소유할 회원 또는 가맹점 Party
     * @param institution 지갑 발급 기관
     * @return 생성되어 WALLET 테이블에 저장된 지갑
     */
    public Wallet createWallet(Party party, Institution institution) {
        GeneratedWallet generatedWallet = generateWallet();

        Wallet wallet =
                Wallet.builder()
                        .party(party)
                        .institution(institution)
                        .address(generatedWallet.address())
                        .build();

        bankWalletService.save(
                BankWallet.builder()
                        .institution(institution)
                        .walletAddress(generatedWallet.address())
                        .balance(INITIAL_BANK_WALLET_BALANCE)
                        .encryptedPrivateKey(generatedWallet.privateKey())
                        .build());

        return walletRepository.save(wallet);
    }

    private GeneratedWallet generateWallet() {
        try {
            ECKeyPair keyPair = Keys.createEcKeyPair();
            String address = Keys.toChecksumAddress(Keys.getAddress(keyPair));
            String privateKey =
                    Numeric.toHexStringNoPrefixZeroPadded(
                            keyPair.getPrivateKey(), PRIVATE_KEY_HEX_LENGTH);

            return new GeneratedWallet(address, privateKey);
        } catch (InvalidAlgorithmParameterException
                | NoSuchAlgorithmException
                | NoSuchProviderException e) {
            throw new BusinessException(WalletErrorCode.WALLET_CREATION_FAILED);
        }
    }

    private record GeneratedWallet(String address, String privateKey) {}
}
