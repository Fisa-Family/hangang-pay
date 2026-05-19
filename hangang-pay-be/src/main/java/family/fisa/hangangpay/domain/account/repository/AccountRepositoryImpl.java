package family.fisa.hangangpay.domain.account.repository;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.jpa.AccountJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepository {

    private final AccountJpaRepository accountJpaRepository;

    @Override
    public List<Account> findAllByParty_Id(Long partyId) {
        return accountJpaRepository.findAllByParty_Id(partyId);
    }

    @Override
    public long countByParty_Id(Long partyId) {
        return accountJpaRepository.countByParty_Id(partyId);
    }

    @Override
    public boolean existsByParty_IdAndAccountNumber(Long partyId, String accountNumber) {
        return accountJpaRepository.existsByParty_IdAndAccountNumber(partyId, accountNumber);
    }

    @Override
    public Optional<Account> findByIdAndParty_Id(Long id, Long partyId) {
        return accountJpaRepository.findByIdAndParty_Id(id, partyId);
    }

    @Override
    public Optional<Account> findByParty_IdAndAccountType(Long partyId, AccountType accountType) {
        return accountJpaRepository.findByParty_IdAndAccountType(partyId, accountType);
    }

    @Override
    public Account save(Account account) {
        return accountJpaRepository.save(account);
    }

    @Override
    public void delete(Account account) {
        accountJpaRepository.delete(account);
    }
}
