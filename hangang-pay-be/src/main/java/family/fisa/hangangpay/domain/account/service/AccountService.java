package family.fisa.hangangpay.domain.account.service;

import family.fisa.hangangpay.domain.account.dto.AccountAddRequest;
import family.fisa.hangangpay.domain.account.dto.AccountListResponse;
import family.fisa.hangangpay.domain.account.dto.AccountResponse;
import family.fisa.hangangpay.domain.account.dto.PrimaryAccountResponse;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.BankAccount;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.BankAccountRepository;
import family.fisa.hangangpay.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
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

    /** 계좌 리포지토리 */
    private final AccountRepository accountRepository;

    /** 금융기관 리포지토리 */
    private final InstitutionRepository institutionRepository;

    /** 은행 원장 계좌 리포지토리 */
    private final BankAccountRepository bankAccountRepository;

    /** 파티 리포지토리 */
    private final PartyRepository partyRepository;

    /** 현재 로그인한 사용자의 등록 계좌 목록 조회 메서드 */
    @Transactional(readOnly = true)
    public AccountListResponse getAccounts(Long partyId) {
        // 파티 식별자 기준 전체 계좌 목록 조회
        List<Account> accounts = accountRepository.findAllByParty_Id(partyId);
        log.info("계좌 목록 조회: partyId={}, count={}", partyId, accounts.size());

        // 엔티티 목록을 응답 DTO 목록으로 변환
        List<AccountResponse> accountResponses =
                accounts.stream().map(AccountResponse::from).toList();

        // 응답 DTO 목록과 전체 계좌 수를 담은 래퍼 반환
        return AccountListResponse.builder()
                .accounts(accountResponses)
                .totalCount(accountResponses.size())
                .build();
    }

    /** 계좌 추가 메서드 */
    @Transactional
    public AccountResponse addAccount(Long partyId, AccountAddRequest request) {
        // 기관 코드로 금융기관 조회, 존재하지 않으면 계좌 없음 오류
        Institution institution =
                institutionRepository
                        .findByInstitutionCode(request.getInstitutionCode())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                AccountErrorCode.BANK_ACCOUNT_NOT_FOUND));

        // 은행 원장에서 계좌번호로 계좌 조회, 없으면 계좌 없음 오류
        BankAccount bankAccount =
                bankAccountRepository
                        .findByInstitution_IdAndAccountNumber(
                                institution.getId(), request.getAccountNumber())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                AccountErrorCode.BANK_ACCOUNT_NOT_FOUND));

        // 동일 계좌 중복 등록 여부 확인
        if (accountRepository.existsByParty_IdAndAccountNumber(
                partyId, request.getAccountNumber())) {
            throw new BusinessException(AccountErrorCode.DUPLICATE_ACCOUNT);
        }

        // 등록 계좌 수 조회 - 한도 초과 및 계좌 유형 결정에 동시 사용
        long count = accountRepository.countByParty_Id(partyId);
        if (count >= 3) {
            throw new BusinessException(AccountErrorCode.MAX_ACCOUNT_EXCEEDED);
        }

        // 첫 번째 계좌는 주거래 계좌로 등록
        AccountType accountType = (count == 0) ? AccountType.PRIMARY : AccountType.SECONDARY;

        // 파티 프록시 참조 로드
        Party party = partyRepository.getReferenceById(partyId);

        // 계좌 저장
        Account account =
                Account.builder()
                        .party(party)
                        .institution(institution)
                        .accountType(accountType)
                        .accountNumber(request.getAccountNumber())
                        .build();
        Account saved = accountRepository.save(account);
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
        // 계좌 식별자와 파티 식별자로 본인 계좌 조회, 없으면 계좌 없음 오류
        Account account =
                accountRepository
                        .findByIdAndParty_Id(accountId, partyId)
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // 마지막 계좌 삭제 여부 확인, 최소 1개 유지
        long count = accountRepository.countByParty_Id(partyId);
        if (count <= 1) {
            throw new BusinessException(AccountErrorCode.LAST_ACCOUNT_DELETE);
        }

        // 주거래 계좌 삭제 여부 확인
        if (account.getAccountType() == AccountType.PRIMARY) {
            throw new BusinessException(AccountErrorCode.PRIMARY_ACCOUNT_DELETE);
        }

        // 계좌 삭제
        accountRepository.delete(account);
        log.info("계좌 삭제 완료: partyId={}, accountId={}", partyId, accountId);
    }

    /** 주거래 계좌 변경 메서드 */
    @Transactional
    public PrimaryAccountResponse changePrimaryAccount(Long partyId, Long accountId) {
        // 대상 계좌 조회, 본인 소유 확인 포함
        Account target =
                accountRepository
                        .findByIdAndParty_Id(accountId, partyId)
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        // 현재 주거래 계좌 조회, 대상과 다를 경우 SECONDARY로 변경하고 식별자 기록
        Long previousPrimaryAccountId = null;
        java.util.Optional<Account> currentPrimary =
                accountRepository.findByParty_IdAndAccountType(partyId, AccountType.PRIMARY);
        if (currentPrimary.isPresent() && !currentPrimary.get().getId().equals(accountId)) {
            previousPrimaryAccountId = currentPrimary.get().getId();
            currentPrimary.get().updateAccountType(AccountType.SECONDARY);
        }

        // 대상 계좌를 주거래 계좌로 변경
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
}
