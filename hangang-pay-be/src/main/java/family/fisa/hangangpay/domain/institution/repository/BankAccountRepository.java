package family.fisa.hangangpay.domain.institution.repository;

import family.fisa.hangangpay.domain.institution.entity.BankAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 은행 원장 계좌 데이터 접근 레이어 */
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    /** 금융기관 식별자와 계좌번호로 원장 계좌 단건 조회 */
    Optional<BankAccount> findByInstitution_IdAndAccountNumber(
            Long institutionId, String accountNumber);
}
