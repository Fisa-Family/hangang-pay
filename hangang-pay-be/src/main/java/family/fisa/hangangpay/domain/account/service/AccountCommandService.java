package family.fisa.hangangpay.domain.account.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.BankAccountResponse;
import family.fisa.hangangpay.domain.account.dto.MerchantAccountUpdateRequest;
import family.fisa.hangangpay.domain.account.dto.MerchantAccountUpdateResponse;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpay.domain.merchant.code.error.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
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
    private final InstitutionRepository institutionRepository;
    private final BankClient bankClient;

    public MerchantAccountUpdateResponse updateMerchantSettlementAccount(
        Long partyId, MerchantAccountUpdateRequest request) {

        // 1. 가맹점 조회
        Merchant merchant = merchantRepository.findByParty_Id(partyId)
                                              .orElseThrow(() -> new BusinessException(
                                                  MerchantErrorCode.MERCHANT_NOT_FOUND));

        // 2. 은핸 정보 조회
        Institution institution = institutionRepository.findByInstitutionCode(
                                                           request.institutionCode())
                                                       .orElseThrow(() -> new BusinessException(
                                                           InstitutionErrorCode.INSTITUTION_NOT_FOUND));

        // 3. 은행에서 계좌 가져오기 -> 존재 하는 지 확인
        BankAccountResponse accountResponse = bankClient
            .getBankAccount(institution.getId(), request.accountNumber());

        if (accountResponse == null) {
            throw new BusinessException(AccountErrorCode.BANK_ACCOUNT_NOT_FOUND);
        }

        // 4. 정산 계좌가 았으면 수정, 없으면 생성
        Optional<Account> existing = accountRepository.findByParty_IdAndAccountType(partyId,
            AccountType.SETTLEMENT);
        Account account;

        // 계좌가 존재한는 경우 업데이트
        if (existing.isPresent()) {
            account = existing.get();
            account.update(institution, request.accountNumber());

            log.info(
                "가맹점 정산 계좌 수정: partyId={}, accountId={}, institutionCode={}",
                partyId,
                account.getId(),
                institution.getInstitutionCode());

        } else { // 계좌가 존재하지 않는 경우 새로 생성
            account = Account.builder()
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
