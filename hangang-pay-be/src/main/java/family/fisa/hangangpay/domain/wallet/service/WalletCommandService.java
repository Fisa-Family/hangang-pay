package family.fisa.hangangpay.domain.wallet.service;

import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WalletCommandService {

    // TODO: 지갑 생성 API
    public Wallet createWallet(Party party, Institution institution) {
        throw new BusinessException(UserErrorCode.WALLET_CREATION_FAILED);
    }
}
