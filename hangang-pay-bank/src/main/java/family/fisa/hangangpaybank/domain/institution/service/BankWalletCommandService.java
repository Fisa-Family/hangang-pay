package family.fisa.hangangpaybank.domain.institution.service;

import family.fisa.hangangpaybank.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.institution.dto.request.CreateBankWalletRequest;
import family.fisa.hangangpaybank.domain.institution.dto.response.BankWalletResponse;
import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

@Service
@RequiredArgsConstructor
@Transactional
public class BankWalletCommandService {

    private final BankWalletRepository bankWalletRepository;
    private final InstitutionRepository institutionRepository;
    private final WalletKeyCipher walletKeyCipher;

    public BankWallet save(BankWallet bankWallet) {
        return bankWalletRepository.save(bankWallet);
    }

    public BankWalletResponse create(CreateBankWalletRequest request) {
        // 1. 소속 기관 조회
        Institution institution =
                institutionRepository
                        .findById(request.institutionId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode.INSTITUTION_NOT_FOUND));

        // 2. Web3j로 EC keypair 생성
        ECKeyPair keyPair;
        try {
            keyPair = Keys.createEcKeyPair();
        } catch (InvalidAlgorithmParameterException
                | NoSuchAlgorithmException
                | NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
        String walletAddress = "0x" + Keys.getAddress(keyPair);
        String privateKeyHex = keyPair.getPrivateKey().toString(16);

        // 3. private key 암호화
        String encryptedPrivateKey = walletKeyCipher.encryptPrivateKey(privateKeyHex);

        // 4. BankWallet 저장
        BankWallet bankWallet =
                BankWallet.builder()
                        .institution(institution)
                        .walletAddress(walletAddress)
                        .balance(BigDecimal.ZERO)
                        .encryptedPrivateKey(encryptedPrivateKey)
                        .build();
        BankWallet saved = bankWalletRepository.save(bankWallet);

        return BankWalletResponse.from(saved);
    }
}
