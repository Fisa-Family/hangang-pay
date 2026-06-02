package family.fisa.hangangpaybank.domain.institution.service;

import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.institution.dto.response.BankWalletResponse;
import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.repository.BankWalletRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankWalletQueryService {

    private static final BigInteger TOKEN_DECIMALS = BigInteger.TEN.pow(18);

    private final BankWalletRepository bankWalletRepository;
    private final ContractCallService contractCallService;

    public BankWalletResponse getByWalletAddress(String walletAddress) {
        // 1. 지갑 주소로 조회
        BankWallet bankWallet =
                bankWalletRepository
                        .findByWalletAddress(walletAddress)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode.BANK_WALLET_NOT_FOUND));

        // 2. 컨트랙트 balanceOf 기준으로 Response 반환
        return BankWalletResponse.from(bankWallet, getTokenBalance(bankWallet));
    }

    private BigDecimal getTokenBalance(BankWallet bankWallet) {
        BigInteger tokenBalance = contractCallService.getBalance(bankWallet.getWalletAddress());
        return new BigDecimal(tokenBalance).divide(new BigDecimal(TOKEN_DECIMALS));
    }
}
