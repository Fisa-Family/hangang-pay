package family.fisa.hangangpay.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.merchant.code.error.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.MerchantMyPageResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MerchantMyPageQueryServiceTest {
    @Mock MerchantRepository merchantRepository;
    @Mock AccountRepository accountRepository;

    @Mock Institution institution;

    @InjectMocks MerchantQueryService merchantQueryService;

    @Test
    void 가맹점_없을_경우() {
        // given
        given(merchantRepository.findByParty_Id(anyLong())).willReturn(Optional.empty());

        // when & then.
        assertThatThrownBy(() -> merchantQueryService.getMyPage(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(MerchantErrorCode.MERCHANT_NOT_FOUND);
    }

    @Test
    void 정산계좌_없을_경우() {
        // given
        given(merchantRepository.findByParty_Id(anyLong()))
                .willReturn(Optional.of(mock(Merchant.class)));
        given(accountRepository.findByParty_IdAndAccountType(anyLong(), eq(AccountType.SETTLEMENT)))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> merchantQueryService.getMyPage(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(MerchantErrorCode.MERCHANT_SETTLEMENT_ACCOUNT_NOT_FOUND);
    }

    @Test
    void 정상_조회() {
        // given

        given(institution.getInstitutionName()).willReturn("국민은행");

        Party party = mock(Party.class);

        given(party.getId()).willReturn(10L);

        Merchant merchant = Merchant.builder().id(1L).party(party).build();

        Account account =
                Account.builder()
                        .id(1L)
                        .institution(institution)
                        .accountType(AccountType.SETTLEMENT)
                        .accountNumber("1234567890")
                        .build();

        given(merchantRepository.findByParty_Id(anyLong())).willReturn(Optional.of(merchant));
        given(accountRepository.findByParty_IdAndAccountType(anyLong(), eq(AccountType.SETTLEMENT)))
                .willReturn(Optional.of(account));

        // when
        MerchantMyPageResponse result = merchantQueryService.getMyPage(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.merchantId()).isEqualTo(1L);
        assertThat(result.settlementAccount()).isNotNull();
    }
}
