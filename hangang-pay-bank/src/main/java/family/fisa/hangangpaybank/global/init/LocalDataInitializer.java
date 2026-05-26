package family.fisa.hangangpaybank.global.init;

import family.fisa.hangangpaybank.domain.institution.entity.BankAccount;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final InstitutionRepository institutionRepository;
    private final BankAccountRepository bankAccountRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (institutionRepository.count() > 0) {
            log.info("[bank-local-seed] skipped");
            return;
        }
        log.info("[bank-local-seed] start");

        seedInstitutions();
        seedBankAccounts();

        log.info(
                "[bank-local-seed] done: institutions={}, accounts={}",
                institutionRepository.count(),
                bankAccountRepository.count());
    }

    /**
     * BC팀 제공 institution 시드. id가 명시적이라 JPA save() 대신 native SQL. walletAddress /
     * encryptedPrivateKey / enodeUrl / rpcEndpoint 는 Besu 노드 운영 데이터.
     */
    private void seedInstitutions() {
        jdbcTemplate.update(
                """
            INSERT INTO institution (
                id, institution_code, institution_name,
                account_number, wallet_address, encrypted_private_key,
                enode_url, rpc_endpoint,
                created_at, updated_at
            ) VALUES
                (1, 'BoK', 'Bank of Korea',
                 '100-000-000001',
                 '0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73',
                 '8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63',
                 'enode://7fa5133d55c65f610f8a75a69a6dcd35e5a3bf26b23c070e4ce43e578daea0e2689b1b1938a44c32bd1cd71b143b420a00
7052ddb5048428ddaf99a21e75632f@172.16.239.11:30303',
                 'http://localhost:8545',
                 NOW(6), NOW(6)),
                (2, 'WR', 'Woori Bank',
                 '200-000-000001',
                 '0x627306090abaB3A6e1400e9345bC60c78a8BEf57',
                 'c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3',
                 'enode://2b48a77f024713797162d9257b3824cf32cb232d57c243b27deab4e6715917d594b0a666ac639f096be1eea5b195369eb5
803b89bf7343c2d419fa0bf50e724b@172.16.239.12:30303',
                 'http://localhost:8547',
                 NOW(6), NOW(6)),
                (3, 'SH', 'Shinhan Bank',
                 '300-000-000001',
                 '0xf17f52151EbEF6C7334FAD080c5704D77216b732',
                 'ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f',
                 'enode://939642618c06dac18b0028d795a1b5181c6de8364bb133dff49e5da2baf113bb4ea61c741fd726526d49292b67bdca3599
f886223d8870beca6d497ad2f9641c@172.16.239.13:30303',
                 'http://localhost:8549',
                 NOW(6), NOW(6)),
                (4, 'HN', 'Hana Bank',
                 '400-000-000001',
                 '0xE9BA79E62a58225065bF24313896CD332dAFCB3C',
                 'fdad4ce4c7c8382ea0357ad12071156ba54963cabed82f415e24c43f537fe784',
                 'enode://29c55b7ab10d198407fb2338e1f8634f93189caab3a4447cf71bf7f2bfedac92820afc0849f996847255d4e2da6a755486
af76516babce4af41dd3c23fdaf0d6@172.16.239.14:30303',
                 'http://localhost:8551',
                 NOW(6), NOW(6))
            """);
    }

    private void seedBankAccounts() {
        Institution woori = institutionRepository.findById(2L).orElseThrow();
        Institution shinhan = institutionRepository.findById(3L).orElseThrow();

        bankAccountRepository.save(
                BankAccount.builder()
                        .institution(woori)
                        .accountNumber("1002-123-456789")
                        .ownerName("테스트유저")
                        .balance(new BigDecimal("1000000.0000"))
                        .build());
        bankAccountRepository.save(
                BankAccount.builder()
                        .institution(shinhan)
                        .accountNumber("110-987-654321")
                        .ownerName("한강떡볶이")
                        .balance(BigDecimal.ZERO.setScale(4))
                        .build());
    }
}
