package family.fisa.hangangpaybank.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.institution.entity.BankAccount;
import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.institution.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.domain.ledger.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeStateWriterTest {

    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String WALLET_ADDRESS = "0x0000000000000000000000000000000000000001";
    private static final String ACCOUNT_NUMBER = "1002-123-456789";
    private static final BigInteger TOKEN_DECIMALS = BigInteger.TEN.pow(18);

    @Mock private InstitutionRepository institutionRepository;
    @Mock private BankWalletRepository bankWalletRepository;
    @Mock private BankAccountRepository bankAccountRepository;
    @Mock private BlockchainLedgerRepository blockchainLedgerRepository;
    @Mock private AccountLedgerRepository accountLedgerRepository;
    @Mock private ContractCallService contractCallService;

    @InjectMocks private ExchangeStateWriter exchangeStateWriter;

    @Test
    @DisplayName("환전 선점 시 컨트랙트 balanceOf 기준으로 잔액을 검증하고 PENDING ledger를 저장한다")
    void claimExchangeUsesContractBalance() {
        Institution institution = institution();
        BankWallet bankWallet = bankWallet(institution);
        ExchangeRequest request =
                new ExchangeRequest(
                        TRANSACTION_UUID, 1L, WALLET_ADDRESS, ACCOUNT_NUMBER, new BigDecimal("10"));
        BlockchainLedger savedLedger =
                BlockchainLedger.builder()
                        .id(5L)
                        .institution(institution)
                        .idempotentKey(TRANSACTION_UUID)
                        .status(BlockchainTxStatus.PENDING)
                        .build();

        given(institutionRepository.findById(1L)).willReturn(Optional.of(institution));
        given(bankWalletRepository.findByWalletAddress(WALLET_ADDRESS))
                .willReturn(Optional.of(bankWallet));
        given(contractCallService.getBalance(WALLET_ADDRESS))
                .willReturn(toTokenUnit(new BigDecimal("100")));
        given(bankAccountRepository.findByInstitution_IdAndAccountNumber(1L, ACCOUNT_NUMBER))
                .willReturn(Optional.of(bankAccount(institution)));
        given(blockchainLedgerRepository.save(any(BlockchainLedger.class))).willReturn(savedLedger);

        Long ledgerId = exchangeStateWriter.claimExchange(request);

        assertThat(ledgerId).isEqualTo(5L);
    }

    @Test
    @DisplayName("환전 선점 시 컨트랙트 잔액이 부족하면 잔액 부족 예외를 던진다")
    void claimExchangeThrowsWhenContractBalanceInsufficient() {
        Institution institution = institution();
        BankWallet bankWallet = bankWallet(institution);
        ExchangeRequest request =
                new ExchangeRequest(
                        TRANSACTION_UUID, 1L, WALLET_ADDRESS, ACCOUNT_NUMBER, new BigDecimal("10"));

        given(institutionRepository.findById(1L)).willReturn(Optional.of(institution));
        given(bankWalletRepository.findByWalletAddress(WALLET_ADDRESS))
                .willReturn(Optional.of(bankWallet));
        given(contractCallService.getBalance(WALLET_ADDRESS))
                .willReturn(toTokenUnit(new BigDecimal("1")));

        assertThatThrownBy(() -> exchangeStateWriter.claimExchange(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(TransactionErrorCode.TRANSACTION_INSUFFICIENT_BALANCE);
    }

    private static Institution institution() {
        return Institution.builder()
                .id(1L)
                .institutionCode("WOORI")
                .institutionName("Woori Bank")
                .build();
    }

    private static BankWallet bankWallet(Institution institution) {
        return BankWallet.builder()
                .id(1L)
                .institution(institution)
                .walletAddress(WALLET_ADDRESS)
                .encryptedPrivateKey("encrypted")
                .build();
    }

    private static BankAccount bankAccount(Institution institution) {
        return BankAccount.builder()
                .id(1L)
                .institution(institution)
                .accountNumber(ACCOUNT_NUMBER)
                .ownerName("tester")
                .balance(new BigDecimal("100000"))
                .build();
    }

    private static BigInteger toTokenUnit(BigDecimal amount) {
        return amount.multiply(new BigDecimal(TOKEN_DECIMALS)).toBigInteger();
    }
}
