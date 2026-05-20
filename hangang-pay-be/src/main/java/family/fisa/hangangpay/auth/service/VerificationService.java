package family.fisa.hangangpay.auth.service;

import family.fisa.hangangpay.auth.code.error.AuthErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** SMS 및 계좌 1원 인증 처리 서비스 */
@Slf4j
@Service
public class VerificationService {

    private static final String SESSION_SMS_CODE = "sms_code";
    private static final String SESSION_SMS_PHONE = "sms_phone";
    private static final String SESSION_ACCOUNT_CODE = "account_code";
    private static final String SESSION_ACCOUNT_NUMBER = "account_number";

    private final Random random = new Random();

    /** SMS 인증 코드 발송 */
    public String sendSms(String phoneNumber, HttpSession session) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        session.setAttribute(SESSION_SMS_CODE, code);
        session.setAttribute(SESSION_SMS_PHONE, phoneNumber);
        log.info("SMS 인증 코드 발송: phoneNumber={}", phoneNumber);
        return code;
    }

    /** SMS 인증 코드 검증 */
    public void verifySms(String phoneNumber, String code, HttpSession session) {
        String savedCode = (String) session.getAttribute(SESSION_SMS_CODE);
        String savedPhone = (String) session.getAttribute(SESSION_SMS_PHONE);

        if (savedCode == null || savedPhone == null) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_NOT_FOUND);
        }
        if (!savedPhone.equals(phoneNumber) || !savedCode.equals(code)) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        session.removeAttribute(SESSION_SMS_CODE);
        session.removeAttribute(SESSION_SMS_PHONE);
        log.info("SMS 인증 완료: phoneNumber={}", phoneNumber);
    }

    /** 계좌 1원 인증 코드 발송 */
    public String sendAccountVerification(String accountNumber, HttpSession session) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        session.setAttribute(SESSION_ACCOUNT_CODE, code);
        session.setAttribute(SESSION_ACCOUNT_NUMBER, accountNumber);
        log.info("계좌 1원 인증 코드 발송: accountNumber={}", accountNumber);
        return code;
    }

    /** 계좌 1원 인증 코드 검증 */
    public void verifyAccount(String accountNumber, String code, HttpSession session) {
        String savedCode = (String) session.getAttribute(SESSION_ACCOUNT_CODE);
        String savedAccount = (String) session.getAttribute(SESSION_ACCOUNT_NUMBER);

        if (savedCode == null || savedAccount == null) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_NOT_FOUND);
        }
        if (!savedAccount.equals(accountNumber) || !savedCode.equals(code)) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        session.removeAttribute(SESSION_ACCOUNT_CODE);
        session.removeAttribute(SESSION_ACCOUNT_NUMBER);
        log.info("계좌 1원 인증 완료: accountNumber={}", accountNumber);
    }
}
