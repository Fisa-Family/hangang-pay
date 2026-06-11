package family.fisa.hangangpay.domain.account.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.CreateBankAccountRequest;
import family.fisa.hangangpay.domain.account.dto.AccountAddRequest;
import family.fisa.hangangpay.domain.account.dto.AccountListResponse;
import family.fisa.hangangpay.domain.account.dto.AccountResponse;
import family.fisa.hangangpay.domain.account.dto.PrimaryAccountResponse;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.service.InstitutionQueryService;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.service.MerchantQueryService;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.service.UserQueryService;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 계좌 비즈니스 로직 처리 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final BigDecimal USER_ACCOUNT_INITIAL_BALANCE = new BigDecimal(1_000_000);
    private static final BigDecimal MERCHANT_ACCOUNT_INITIAL_BALANCE = BigDecimal.ZERO;

    private final AccountRepository accountRepository;
    private final PartyRepository partyRepository;
    private final InstitutionQueryService institutionQueryService;
    private final BankClient bankClient;
    private final UserQueryService userQueryService;
    private final MerchantQueryService merchantQueryService;

    /** 현재 로그인한 사용자의 등록 계좌 목록 조회 메서드 */
    @Transactional(readOnly = true)
    public AccountListResponse getAccounts(Long partyId) {
        // 파티 식별자 기준 전체 계좌 목록 조회
        List<Account> accounts = accountRepository.findAllByParty_Id(partyId);
        log.info("계좌 목록 조회: partyId={}, count={}", partyId, accounts.size());

        // 엔티티 목록을 응답 DTO 목록으로 변환
        List<AccountResponse> accountResponses =
                accounts.stream().map(AccountResponse::from).toList();

        return AccountListResponse.builder()
                .accounts(accountResponses)
                .totalCount(accountResponses.size())
                .build();
    }

    /** 계좌 추가 메서드 */
    @Transactional
    public AccountResponse addAccount(Long partyId, AccountAddRequest request) {
        // 1. 기관 코드로 BE 캐시에서 institution 조회
        Institution institution = institutionQueryService.getByCode(request.getInstitutionCode());

        // 2. 동일 계좌 중복 등록 여부 확인
        if (accountRepository.existsByParty_IdAndAccountNumber(
                partyId, request.getAccountNumber())) {
            throw new BusinessException(AccountErrorCode.DUPLICATE_ACCOUNT);
        }

        // 3. 등록 계좌 수 조회 - 한도 초과 확인
        long count = accountRepository.countByParty_Id(partyId);
        if (count >= 3) {
            throw new BusinessException(AccountErrorCode.MAX_ACCOUNT_EXCEEDED);
        }

        // 4. 회원가입 시 생성된 주거래 계좌 이후 추가되는 계좌는 일반 계좌로 등록
        AccountType accountType = AccountType.SECONDARY;

        // 5. 파티 프록시 참조 로드
        Party party = partyRepository.getReferenceById(partyId);
        AccountOwner accountOwner = resolveAccountOwner(partyId, party.getPartyType());

        // 6. 계좌 저장
        Account account =
                Account.builder()
                        .party(party)
                        .institution(institution)
                        .accountType(accountType)
                        .accountNumber(request.getAccountNumber())
                        .build();
        Account saved = accountRepository.save(account);

        bankClient.createBankAccount(
                new CreateBankAccountRequest(
                        institution.getId(),
                        request.getAccountNumber(),
                        accountOwner.ownerName(),
                        accountOwner.initialBalance()));
        log.info(
                "계좌 추가 완료: partyId={}, accountId={}, accountType={}",
                partyId,
                saved.getId(),
                accountType);

        return AccountResponse.from(saved);
    }

    /** 계좌 삭제 메서드 */
    @Transactional
    public void deleteAccount(Long partyId, Long accountId) {
        Account account =
                accountRepository
                        .findByIdAndParty_Id(accountId, partyId)
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        long count = accountRepository.countByParty_Id(partyId);
        if (count <= 1) {
            throw new BusinessException(AccountErrorCode.LAST_ACCOUNT_DELETE);
        }

        if (account.getAccountType() == AccountType.PRIMARY) {
            throw new BusinessException(AccountErrorCode.PRIMARY_ACCOUNT_DELETE);
        }

        accountRepository.delete(account);
        log.info("계좌 삭제 완료: partyId={}, accountId={}", partyId, accountId);
    }

    /** 주거래 계좌 변경 메서드 */
    @Transactional
    public PrimaryAccountResponse changePrimaryAccount(Long partyId, Long accountId) {
        Account target =
                accountRepository
                        .findByIdAndParty_Id(accountId, partyId)
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        Long previousPrimaryAccountId = null;
        java.util.Optional<Account> currentPrimary =
                accountRepository.findByParty_IdAndAccountType(partyId, AccountType.PRIMARY);
        if (currentPrimary.isPresent() && !currentPrimary.get().getId().equals(accountId)) {
            previousPrimaryAccountId = currentPrimary.get().getId();
            currentPrimary.get().updateAccountType(AccountType.SECONDARY);
        }

        target.updateAccountType(AccountType.PRIMARY);
        log.info(
                "주거래 계좌 변경 완료: partyId={}, accountId={}, previousPrimaryId={}",
                partyId,
                accountId,
                previousPrimaryAccountId);

        return PrimaryAccountResponse.builder()
                .accountId(target.getId())
                .accountType(AccountType.PRIMARY.name())
                .previousPrimaryAccountId(previousPrimaryAccountId)
                .build();
    }

    private AccountOwner resolveAccountOwner(Long partyId, PartyType partyType) {
        if (partyType == PartyType.USER) {
            User user = userQueryService.getByPartyId(partyId);
            return new AccountOwner(user.getUsername(), USER_ACCOUNT_INITIAL_BALANCE);
        }

        Merchant merchant = merchantQueryService.getByPartyId(partyId);
        return new AccountOwner(merchant.getOwnerName(), MERCHANT_ACCOUNT_INITIAL_BALANCE);
    }

    private record AccountOwner(String ownerName, BigDecimal initialBalance) {}
}
