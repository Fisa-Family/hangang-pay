package family.fisa.hangangpay.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.domain.account.dto.MerchantAccountUpdateRequest;
import family.fisa.hangangpay.domain.account.dto.MerchantAccountUpdateResponse;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.code.error.InstitutionErrorCode;
import family.fisa.hangangpay.domain.institution.entity.BankAccount;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.service.InstitutionService;
import family.fisa.hangangpay.domain.merchant.code.error.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountCommandServiceTest {

    @Mock MerchantRepository merchantRepository;
    @Mock AccountRepository accountRepository;
    @Mock InstitutionService institutionService;

    @InjectMocks AccountCommandService service;

    private static final Long PARTY_ID = 1L;
    private static final String INSTITUTION_CODE = "004";
    private static final String ACCOUNT_NUMBER = "1234567890";

    private Merchant merchant;
    private Institution institution;
    private MerchantAccountUpdateRequest request;

    @BeforeEach
    void setUp() {
        Party party = mock(Party.class);
        merchant = Merchant.builder().id(1L).party(party).build();
        institution = mock(Institution.class);
        request = new MerchantAccountUpdateRequest(INSTITUTION_CODE, ACCOUNT_NUMBER);
    }

    @Test
    void 가맹점_없으면_MERCHANT_NOT_FOUND() {
        given(merchantRepository.findByParty_Id(PARTY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMerchantSettlementAccount(PARTY_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(MerchantErrorCode.MERCHANT_NOT_FOUND);
    }

    @Test
    void 기관코드_없으면_INSTITUTION_NOT_FOUND() {
        given(merchantRepository.findByParty_Id(PARTY_ID)).willReturn(Optional.of(merchant));
        given(institutionService.findByInstitutionCode(INSTITUTION_CODE))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMerchantSettlementAccount(PARTY_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(InstitutionErrorCode.INSTITUTION_NOT_FOUND);
    }

    @Test
    void 은행원장_계좌_없으면_BANK_ACCOUNT_NOT_FOUND() {
        given(institution.getId()).willReturn(10L);
        given(merchantRepository.findByParty_Id(PARTY_ID)).willReturn(Optional.of(merchant));
        given(institutionService.findByInstitutionCode(INSTITUTION_CODE))
                .willReturn(Optional.of(institution));
        given(institutionService.findBankAccount(anyLong(), eq(ACCOUNT_NUMBER)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMerchantSettlementAccount(PARTY_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(AccountErrorCode.BANK_ACCOUNT_NOT_FOUND);
    }

    @Test
    void 기존_SETTLEMENT_계좌_있으면_수정() {
        given(institution.getId()).willReturn(10L);
        given(institution.getInstitutionCode()).willReturn(INSTITUTION_CODE);
        given(institution.getInstitutionName()).willReturn("국민은행");
        given(merchantRepository.findByParty_Id(PARTY_ID)).willReturn(Optional.of(merchant));
        given(institutionService.findByInstitutionCode(INSTITUTION_CODE))
                .willReturn(Optional.of(institution));
        given(institutionService.findBankAccount(anyLong(), eq(ACCOUNT_NUMBER)))
                .willReturn(Optional.of(mock(BankAccount.class)));

        Account existing =
                Account.builder()
                        .id(5L)
                        .party(merchant.getParty())
                        .institution(institution)
                        .accountType(AccountType.SETTLEMENT)
                        .accountNumber("0000000000")
                        .build();
        given(accountRepository.findByParty_IdAndAccountType(PARTY_ID, AccountType.SETTLEMENT))
                .willReturn(Optional.of(existing));

        MerchantAccountUpdateResponse response =
                service.updateMerchantSettlementAccount(PARTY_ID, request);

        assertThat(response.accountId()).isEqualTo(5L);
        assertThat(response.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void 기존_SETTLEMENT_계좌_없으면_생성() {
        given(institution.getId()).willReturn(10L);
        given(institution.getInstitutionCode()).willReturn(INSTITUTION_CODE);
        given(institution.getInstitutionName()).willReturn("국민은행");
        given(merchantRepository.findByParty_Id(PARTY_ID)).willReturn(Optional.of(merchant));
        given(institutionService.findByInstitutionCode(INSTITUTION_CODE))
                .willReturn(Optional.of(institution));
        given(institutionService.findBankAccount(anyLong(), eq(ACCOUNT_NUMBER)))
                .willReturn(Optional.of(mock(BankAccount.class)));
        given(accountRepository.findByParty_IdAndAccountType(PARTY_ID, AccountType.SETTLEMENT))
                .willReturn(Optional.empty());

        Account saved =
                Account.builder()
                        .id(99L)
                        .party(merchant.getParty())
                        .institution(institution)
                        .accountType(AccountType.SETTLEMENT)
                        .accountNumber(ACCOUNT_NUMBER)
                        .build();
        given(accountRepository.save(any())).willReturn(saved);

        MerchantAccountUpdateResponse response =
                service.updateMerchantSettlementAccount(PARTY_ID, request);

        assertThat(response.accountId()).isEqualTo(99L);
        assertThat(response.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
        verify(accountRepository).save(any());
    }
}
