package family.fisa.hangangpay.domain.account.repository;

import family.fisa.hangangpay.domain.account.entity.Account;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 계좌 데이터 접근 레이어 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    /** 파티 식별자 기준 전체 계좌 목록 조회 */
    List<Account> findAllByParty_Id(Long partyId);

    /** 파티 식별자 기준 등록 계좌 수 조회 */
    long countByParty_Id(Long partyId);

    /** 파티 식별자와 계좌번호로 중복 등록 여부 확인 */
    boolean existsByParty_IdAndAccountNumber(Long partyId, String accountNumber);

    /** 계좌 식별자와 파티 식별자로 본인 계좌 단건 조회 */
    java.util.Optional<Account> findByIdAndParty_Id(Long id, Long partyId);

    /** 파티 식별자와 계좌 유형으로 단건 조회 - 현재 주거래 계좌 탐색용 */
    java.util.Optional<Account> findByParty_IdAndAccountType(
            Long partyId, family.fisa.hangangpay.domain.account.entity.AccountType accountType);
}
