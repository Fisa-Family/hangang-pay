package family.fisa.hangangpay.domain.institution.service;

import family.fisa.hangangpay.domain.institution.entity.BankWallet;
import family.fisa.hangangpay.domain.institution.repository.BankWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BankWalletCommandService {

    private final BankWalletRepository bankWalletRepository;

    public BankWallet save(BankWallet bankWallet) {
        return bankWalletRepository.save(bankWallet);
    }
}
