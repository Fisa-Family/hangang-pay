package family.fisa.hangangpay.domain.user.service;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.dto.UserRegisterRequest;
import family.fisa.hangangpay.domain.user.dto.UserRegisterResponse;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.service.WalletCommandService;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {

    private static final Duration SIGNUP_VERIFICATION_TTL = Duration.ofMinutes(30);

    private final PartyRepository partyRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final WalletCommandService walletCommandService;
    private final InstitutionRepository institutionRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegisterResponse register(UserRegisterRequest request, HttpSession session) {
        validateTerms(request.termsAgreed());
        validatePhoneVerification(request, session);
        validateAccountVerification(request, session);

        userRepository
                .findByPhoneNumberWithParty(request.phoneNumber())
                .ifPresent(
                        user -> {
                            throw new BusinessException(UserErrorCode.DUPLICATE_PHONE_NUMBER);
                        });

        Institution institution =
                institutionRepository
                        .findById(request.institutionId())
                        .orElseThrow(
                                () -> new BusinessException(UserErrorCode.INSTITUTION_NOT_FOUND));

        Party party = partyRepository.save(Party.of(PartyType.USER));
        User user =
                userRepository.save(
                        User.builder()
                                .party(party)
                                .username(request.name())
                                .passwordHash(passwordEncoder.encode(request.password()))
                                .paymentPinHash(passwordEncoder.encode(request.paymentPin()))
                                .phoneNumber(request.phoneNumber())
                                .birthDate(request.birthDate())
                                .build());

        accountRepository.save(
                Account.builder()
                        .party(party)
                        .institution(institution)
                        .accountType(AccountType.PRIMARY)
                        .accountNumber(request.accountNumber())
                        .build());
        walletCommandService.createWallet(party, institution);

        clearSignupSession(session);
        log.info("소비자 회원가입 완료: userId={}, partyId={}", user.getId(), party.getId());
        return new UserRegisterResponse(party.getId(), user.getId());
    }

    private void validateTerms(UserRegisterRequest.TermsAgreed termsAgreed) {
        if (termsAgreed == null
                || !termsAgreed.serviceTerms()
                || !termsAgreed.privacyTerms()
                || !termsAgreed.electronicFinanceTerms()
                || !termsAgreed.localCurrencyTerms()) {
            throw new BusinessException(UserErrorCode.TERMS_NOT_AGREED);
        }
    }

    private void validatePhoneVerification(UserRegisterRequest request, HttpSession session) {
        Boolean verified =
                (Boolean) session.getAttribute(SessionAttributeNames.SIGNUP_PHONE_VERIFIED);
        String phoneNumber =
                (String) session.getAttribute(SessionAttributeNames.SIGNUP_PHONE_NUMBER);
        LocalDateTime verifiedAt =
                (LocalDateTime)
                        session.getAttribute(SessionAttributeNames.SIGNUP_PHONE_VERIFIED_AT);

        if (!Boolean.TRUE.equals(verified) || phoneNumber == null || verifiedAt == null) {
            throw new BusinessException(UserErrorCode.SIGNUP_PHONE_NOT_VERIFIED);
        }
        if (!phoneNumber.equals(request.phoneNumber())) {
            throw new BusinessException(UserErrorCode.SIGNUP_PHONE_MISMATCH);
        }
        if (isExpired(verifiedAt)) {
            throw new BusinessException(UserErrorCode.SIGNUP_PHONE_VERIFICATION_EXPIRED);
        }
    }

    private void validateAccountVerification(UserRegisterRequest request, HttpSession session) {
        Boolean verified =
                (Boolean) session.getAttribute(SessionAttributeNames.SIGNUP_ACCOUNT_VERIFIED);
        Long institutionId =
                (Long) session.getAttribute(SessionAttributeNames.SIGNUP_INSTITUTION_ID);
        String accountNumber =
                (String) session.getAttribute(SessionAttributeNames.SIGNUP_ACCOUNT_NUMBER);
        LocalDateTime verifiedAt =
                (LocalDateTime)
                        session.getAttribute(SessionAttributeNames.SIGNUP_ACCOUNT_VERIFIED_AT);

        if (!Boolean.TRUE.equals(verified)
                || institutionId == null
                || accountNumber == null
                || verifiedAt == null) {
            throw new BusinessException(UserErrorCode.SIGNUP_ACCOUNT_NOT_VERIFIED);
        }
        if (!institutionId.equals(request.institutionId())
                || !accountNumber.equals(request.accountNumber())) {
            throw new BusinessException(UserErrorCode.SIGNUP_ACCOUNT_MISMATCH);
        }
        if (isExpired(verifiedAt)) {
            throw new BusinessException(UserErrorCode.SIGNUP_ACCOUNT_VERIFICATION_EXPIRED);
        }
    }

    private boolean isExpired(LocalDateTime verifiedAt) {
        return verifiedAt.plus(SIGNUP_VERIFICATION_TTL).isBefore(LocalDateTime.now());
    }

    private void clearSignupSession(HttpSession session) {
        session.removeAttribute(SessionAttributeNames.SIGNUP_PHONE_VERIFIED);
        session.removeAttribute(SessionAttributeNames.SIGNUP_PHONE_NUMBER);
        session.removeAttribute(SessionAttributeNames.SIGNUP_PHONE_VERIFIED_AT);
        session.removeAttribute(SessionAttributeNames.SIGNUP_ACCOUNT_VERIFIED);
        session.removeAttribute(SessionAttributeNames.SIGNUP_INSTITUTION_ID);
        session.removeAttribute(SessionAttributeNames.SIGNUP_ACCOUNT_NUMBER);
        session.removeAttribute(SessionAttributeNames.SIGNUP_ACCOUNT_VERIFIED_AT);
    }
}
