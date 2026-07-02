package family.fisa.hangangpay.domain.transaction.internal;

import java.time.LocalDateTime;

/**
 * 승인번호 생성기. 형식은 {@code APV-YYYY-NNNNNNNN} (연도 + transaction.id 8자리 zero padding). 승인번호는 transaction
 * 저장으로 id를 확보한 뒤 생성한다. 예: id=25 → {@code APV-2026-00000025}.
 */
public final class ApprovalNumberGenerator {

    private ApprovalNumberGenerator() {}

    /** transaction.id로 승인번호를 생성한다. */
    public static String generate(Long transactionId) {
        return "APV-" + LocalDateTime.now().getYear() + "-" + String.format("%08d", transactionId);
    }
}
