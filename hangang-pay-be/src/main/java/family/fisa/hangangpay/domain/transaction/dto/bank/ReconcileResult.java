package family.fisa.hangangpay.domain.transaction.dto.bank;

public enum ReconcileResult {

    /** bank가 SUCCESS 응답. UNKNOWN → SUCCESS 마킹 완료 */
    RECONCILED_SUCCESS,

    /** bank가 거래 없음 응답. UNKNOWN → FAILED 마킹 완료 */
    RECONCILED_FAILED,

    /** PENDING 상태가 아니어서 reconcile 불필요. 별도 처리 없음 */
    SKIPPED,

    /** 시도 횟수 임계 도달 등 reconcile 자체가 더 이상 진행 불가. 수동 처리 위임 */
    RECONCILE_ERROR
}
