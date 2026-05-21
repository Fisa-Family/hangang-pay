package family.fisa.hangangpay.global.session;

public final class SessionAttributeNames {

    public static final String USER_ID = "userId";
    public static final String MERCHANT_ID = "merchantId";
    public static final String PARTY_ID = "partyId";
    public static final String ROLE = "role";

    // 회원가입 중간 단계 저장을 위한 세션 값
    public static final String SIGNUP_PHONE_VERIFIED = "signupPhoneVerified";
    public static final String SIGNUP_PHONE_NUMBER = "signupPhoneNumber";
    public static final String SIGNUP_PHONE_VERIFIED_AT = "signupPhoneVerifiedAt";

    public static final String SIGNUP_ACCOUNT_VERIFIED = "signupAccountVerified";
    public static final String SIGNUP_INSTITUTION_ID = "signupInstitutionId";
    public static final String SIGNUP_ACCOUNT_NUMBER = "signupAccountNumber";
    public static final String SIGNUP_ACCOUNT_VERIFIED_AT = "signupAccountVerifiedAt";

    private SessionAttributeNames() {}
}
