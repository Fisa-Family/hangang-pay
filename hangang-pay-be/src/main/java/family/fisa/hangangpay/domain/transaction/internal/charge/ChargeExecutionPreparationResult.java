package family.fisa.hangangpay.domain.transaction.internal.charge;

import family.fisa.hangangpay.domain.transaction.dto.response.ChargeExecuteResponse;

/** 충전 실행 준비 결과 (신규 실행 또는 멱등 재사용) */
public record ChargeExecutionPreparationResult(
        ChargeExecutionPrepared prepared, // 실행 준비 데이터 (신규 요청 시)
        ChargeExecuteResponse responseSnapshot) { // 멱등 재사용 응답 (중복 요청 시)

    /** 신규 실행 준비 완료 결과 생성 */
    public static ChargeExecutionPreparationResult prepared(ChargeExecutionPrepared prepared) {
        return new ChargeExecutionPreparationResult(prepared, null);
    }

    /** 멱등 재사용 snapshot 결과 생성 */
    public static ChargeExecutionPreparationResult snapshot(ChargeExecuteResponse snapshot) {
        return new ChargeExecutionPreparationResult(null, snapshot);
    }

    /** snapshot 보유 여부 반환 */
    public boolean hasSnapshot() {
        return responseSnapshot != null;
    }
}
