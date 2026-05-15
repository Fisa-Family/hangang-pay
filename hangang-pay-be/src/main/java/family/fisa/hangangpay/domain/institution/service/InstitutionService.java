package family.fisa.hangangpay.domain.institution.service;

import family.fisa.hangangpay.domain.institution.entity.BankAccount;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.BankAccountRepository;
import family.fisa.hangangpay.domain.institution.repository.InstitutionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 금융기관 비즈니스 로직 처리 서비스 */
@Service
@RequiredArgsConstructor
public class InstitutionService {

    /** 금융기관 레포지토리 */
    private final InstitutionRepository institutionRepository;

    /** 은행 원장 계좌 레포지토리 */
    private final BankAccountRepository bankAccountRepository;

    /** 기관 코드로 금융기관 단건 조회 */
    @Transactional(readOnly = true)
    public Optional<Institution> findByInstitutionCode(String institutionCode) {
        return institutionRepository.findByInstitutionCode(institutionCode);
    }

    /** 금융기관 식별자와 계좌번호로 은행 원장 계좌 단건 조회 */
    @Transactional(readOnly = true)
    public Optional<BankAccount> findBankAccount(Long institutionId, String accountNumber) {
        return bankAccountRepository.findByInstitution_IdAndAccountNumber(
                institutionId, accountNumber);
    }
}
