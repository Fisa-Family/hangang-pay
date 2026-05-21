package family.fisa.hangangpay.domain.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.domain.institution.entity.BankWallet;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.service.BankWalletService;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private BankWalletService bankWalletService;

    @InjectMocks private WalletService walletService;

    @Test
    @DisplayName("회원/가맹점 지갑을 생성해 WALLET과 BANK_WALLET에 저장한다")
    void createWallet() {
        Party party = Party.of(PartyType.USER);
        Institution institution = Institution.builder().id(2L).institutionName("우리은행").build();

        given(walletRepository.save(any(Wallet.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Wallet wallet = walletService.createWallet(party, institution);

        assertThat(wallet.getParty()).isSameAs(party);
        assertThat(wallet.getInstitution()).isSameAs(institution);
        assertThat(wallet.getAddress()).startsWith("0x").hasSize(42);

        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(walletCaptor.capture());

        ArgumentCaptor<BankWallet> bankWalletCaptor = ArgumentCaptor.forClass(BankWallet.class);
        verify(bankWalletService).save(bankWalletCaptor.capture());
        BankWallet bankWallet = bankWalletCaptor.getValue();
        assertThat(bankWallet.getInstitution()).isSameAs(institution);
        assertThat(bankWallet.getWalletAddress()).isEqualTo(walletCaptor.getValue().getAddress());
        assertThat(bankWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bankWallet.getEncryptedPrivateKey()).hasSize(64);
    }
}
