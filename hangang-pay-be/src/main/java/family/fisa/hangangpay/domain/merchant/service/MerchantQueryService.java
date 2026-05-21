package family.fisa.hangangpay.domain.merchant.service;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.merchant.code.error.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.MerchantInfoResponse;
import family.fisa.hangangpay.domain.merchant.dto.MerchantMyPageResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantQueryService {
    private final MerchantRepository merchantRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;

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

    public MerchantInfoResponse getMerchantInfo(Long merchantId) {
        log.info("가맹점 정보 조회 시작. merchantId={}", merchantId);

        Merchant merchant = findMerchantById(merchantId);
        Wallet wallet = findWalletByPartyId(merchant.getParty().getId());

        log.info("가맹점 정보 조회 완료. merchantId={}", merchantId);

        return MerchantInfoResponse.from(merchant, wallet);
    }

    private Merchant findMerchantById(Long merchantId) {
        return merchantRepository
                .findById(merchantId)
                .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
    }

    private Wallet findWalletByPartyId(Long partyId) {
        return walletRepository
                .findByParty_Id(partyId)
                .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
    }
}
