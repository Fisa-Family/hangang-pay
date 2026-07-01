package family.fisa.hangangpay.auth.service;

import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_ACCOUNT_NUMBER;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_ACCOUNT_VERIFIED;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_ACCOUNT_VERIFIED_AT;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_INSTITUTION_ID;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_PHONE_NUMBER;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_PHONE_VERIFIED;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_PHONE_VERIFIED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import family.fisa.hangangpay.auth.code.AuthErrorCode;
import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock private BankClient bankClient;

    private static final String PHONE_NUMBER = "010-1234-5678";
    private static final Long INSTITUTION_ID = 1L;
    private static final String ACCOUNT_NUMBER = "1002123456789";

    @InjectMocks private VerificationService verificationService;

    @Test
    @DisplayName("SMS 인증 성공 시 회원가입용 휴대폰 인증 상태를 세션에 저장한다")
    void verifySmsStoresSignupPhoneVerificationSession() {
        MockHttpSession session = new MockHttpSession();
        String code = verificationService.sendSms(PHONE_NUMBER, session);

        verificationService.verifySms(PHONE_NUMBER, code, session);

        assertThat(session.getAttribute(SIGNUP_PHONE_VERIFIED)).isEqualTo(true);
        assertThat(session.getAttribute(SIGNUP_PHONE_NUMBER)).isEqualTo(PHONE_NUMBER);
        assertThat(session.getAttribute(SIGNUP_PHONE_VERIFIED_AT))
                .isInstanceOf(LocalDateTime.class);
        assertThat(session.getAttribute("sms_code")).isNull();
        assertThat(session.getAttribute("sms_phone")).isNull();
        assertThat(session.getAttribute("sms_expires_at")).isNull();
    }

    @Test
    @DisplayName("계좌 1원 인증 성공 시 회원가입용 계좌 인증 상태를 세션에 저장한다")
    void verifyAccountStoresSignupAccountVerificationSession() {
        MockHttpSession session = new MockHttpSession();
        String code =
                verificationService.sendAccountVerification(
                        INSTITUTION_ID, ACCOUNT_NUMBER, session);

        verificationService.verifyAccount(INSTITUTION_ID, ACCOUNT_NUMBER, code, session);

        assertThat(session.getAttribute(SIGNUP_ACCOUNT_VERIFIED)).isEqualTo(true);
        assertThat(session.getAttribute(SIGNUP_INSTITUTION_ID)).isEqualTo(INSTITUTION_ID);
        assertThat(session.getAttribute(SIGNUP_ACCOUNT_NUMBER)).isEqualTo(ACCOUNT_NUMBER);
        assertThat(session.getAttribute(SIGNUP_ACCOUNT_VERIFIED_AT))
                .isInstanceOf(LocalDateTime.class);
    }

    @Test
    @DisplayName("계좌 1원 인증 시 기관 식별자가 일치하지 않으면 예외를 던진다")
    void verifyAccountWithDifferentInstitutionId() {
        MockHttpSession session = new MockHttpSession();
        String code =
                verificationService.sendAccountVerification(
                        INSTITUTION_ID, ACCOUNT_NUMBER, session);

        assertThatThrownBy(
                        () -> verificationService.verifyAccount(2L, ACCOUNT_NUMBER, code, session))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
    }
}
