package family.fisa.hangangpay.domain.transaction.dto.response;

final class UsernameMasker {

    private UsernameMasker() {}

    static String mask(String username) {
        if (username == null || username.isBlank()) {
            return "알 수 없는 사용자";
        }
        if (username.length() == 1) {
            return "*";
        }
        if (username.length() == 2) {
            return username.charAt(0) + "*";
        }
        return username.charAt(0) + "*" + username.charAt(username.length() - 1);
    }
}
