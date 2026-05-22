package family.fisa.hangangpay.domain.account.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.domain.account.dto.MerchantAccountUpdateRequest;
import family.fisa.hangangpay.domain.account.dto.MerchantAccountUpdateResponse;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.service.InstitutionQueryService;
import family.fisa.hangangpay.domain.merchant.code.error.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AccountCommandService {

    private final MerchantRepository merchantRepository;
    private final AccountRepository accountRepository;
    private final InstitutionQueryService institutionQueryService;
    private final BankClient bankClient;

    public MerchantAccountUpdateResponse updateMerchantSettlementAccount(
            Long partyId, MerchantAccountUpdateRequest request) {

        Merchant merchant =
                merchantRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(
                                () -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

        Institution institution = institutionQueryService.getByCode(request.institutionCode());

        bankClient.getBankAccount(institution.getId(), request.accountNumber());

        Optional<Account> existing =
                accountRepository.findByParty_IdAndAccountType(partyId, AccountType.SETTLEMENT);

        Account account;
        if (existing.isPresent()) {
            account = existing.get();
            account.update(institution, request.accountNumber());
            log.info(
                    "가맹점 정산 계좌 수정: partyId={}, accountId={}, institutionCode={}",
                    partyId,
                    account.getId(),
                    institution.getInstitutionCode());
        } else {
            account =
                    Account.builder()
                            .party(merchant.getParty())
                            .institution(institution)
                            .accountType(AccountType.SETTLEMENT)
                            .accountNumber(request.accountNumber())
                            .build();
            account = accountRepository.save(account);
            log.info(
                    "가맹점 정산 계좌 생성: partyId={}, accountId={}, institutionCode={}",
                    partyId,
                    account.getId(),
                    institution.getInstitutionCode());
        }

        return MerchantAccountUpdateResponse.from(account);
    }
}
