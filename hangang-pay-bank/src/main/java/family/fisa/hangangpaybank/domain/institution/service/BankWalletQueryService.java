package family.fisa.hangangpaybank.domain.institution.service;

import family.fisa.hangangpaybank.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.institution.dto.response.BankWalletResponse;
import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.repository.BankWalletRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankWalletQueryService {

    private final BankWalletRepository bankWalletRepository;

    public BankWalletResponse getByWalletAddress(String walletAddress) {
        // 1. 지갑 주소로 조회
        BankWallet bankWallet =
                bankWalletRepository
                        .findByWalletAddress(walletAddress)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode.BANK_WALLET_NOT_FOUND));

        // 2. Response 반환
        return BankWalletResponse.from(bankWallet);
    }
}
