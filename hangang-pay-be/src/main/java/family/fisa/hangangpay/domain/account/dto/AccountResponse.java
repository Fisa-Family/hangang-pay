package family.fisa.hangangpay.domain.account.dto;

import family.fisa.hangangpay.domain.account.entity.Account;
import lombok.Builder;
import lombok.Getter;

/** 계좌 목록 조회 응답 항목 */
@Getter
@Builder
public class AccountResponse {

    /** 계좌 식별자 */
    private Long accountId;

    /** 금융기관 코드 */
    private String institutionCode;

    /** 금융기관명 */
    private String bankName;

    /** 마스킹 처리된 계좌번호 */
    private String maskedAccountNumber;

    /** 계좌 유형 PRIMARY 또는 SECONDARY */
    private String accountType;

    /** 엔티티를 응답 DTO로 변환하는 팩토리 메서드 */
    public static AccountResponse from(Account account) {
        return AccountResponse.builder()
                .accountId(account.getId())
                .institutionCode(account.getInstitution().getInstitutionCode())
                .bankName(account.getInstitution().getInstitutionName())
                .maskedAccountNumber(maskAccountNumber(account.getAccountNumber()))
                .accountType(account.getAccountType().name())
                .build();
    }

    /** 뒤 4자리만 노출하고 앞 자리는 마스킹 처리하는 계좌번호 변환 메서드 */
    private static String maskAccountNumber(String accountNumber) {
        // 계좌번호가 없거나 4자리 미만인 경우 기본 마스킹 반환
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        // 마지막 4자리만 노출하고 나머지는 마스킹 처리
        return "****-****-" + accountNumber.substring(accountNumber.length() - 4);
    }
}
