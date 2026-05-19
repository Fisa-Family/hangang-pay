package family.fisa.hangangpay.domain.merchant.service;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.merchant.code.error.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.MerchantMyPageResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantQueryService {
    private final MerchantRepository merchantRepository;
    private final AccountRepository accountRepository;

    public MerchantMyPageResponse getMyPage(Long partyId) {
        Merchant merchant =
                merchantRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(
                                () -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

        Account account =
                accountRepository
                        .findByParty_IdAndAccountType(partyId, AccountType.SETTLEMENT)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                MerchantErrorCode
                                                        .MERCHANT_SETTLEMENT_ACCOUNT_NOT_FOUND));

        return MerchantMyPageResponse.from(merchant, account);
    }
}
