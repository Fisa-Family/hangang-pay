package family.fisa.hangangpaybank.global.fault;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 로컬 장애 시나리오 재현용 훅.
 *
 * <p>프로퍼티 미설정 시(기본값 "") 모든 메서드가 no-op이므로 운영 환경에서 영향 없음.
 *
 * <p>실행: --fault.hook.point=HOOK_NAME 또는 --fault.hook.sleep-point=HOOK_NAME
 */
@Slf4j
@Component
public class FaultHookService {

    // ── hook point 상수 ────────────────────────────────────────────────────────
    // 메인 TX 내부 — halt 시 MySQL이 롤백, DB 흔적 없음 (atomicity 검증)
    public static final String AFTER_WALLET_LEDGER_SAVE = "AFTER_WALLET_LEDGER_SAVE";
    public static final String AFTER_BLOCKCHAIN_LEDGER_SAVE = "AFTER_BLOCKCHAIN_LEDGER_SAVE";

    // TX 커밋 이후 (afterCommit 등록) — halt 시 wallet+blockchain+outbox 모두 커밋된 상태로 프로세스 종료
    public static final String AFTER_OUTBOX_SAVE = "AFTER_OUTBOX_SAVE";

    // REQUIRES_NEW 커밋 경계 — halt 시 각각 독립 커밋 후 프로세스 종료
    public static final String AFTER_CHAIN_SUBMIT_BEFORE_MARK_SUBMITTED =
            "AFTER_CHAIN_SUBMIT_BEFORE_MARK_SUBMITTED";
    public static final String AFTER_MARK_SUBMITTED_BEFORE_RECEIPT =
            "AFTER_MARK_SUBMITTED_BEFORE_RECEIPT";

    // ── 필드 (필드 기본값 설정: new FaultHookService()로 테스트에서 no-op 인스턴스 생성 가능) ──
    @Value("${fault.hook.point:}")
    private String haltPoint = "";

    @Value("${fault.hook.sleep-point:}")
    private String sleepPoint = "";

    @Value("${fault.hook.sleep-seconds:30}")
    private int sleepSeconds = 30;

    /**
     * 지정 point에서 JVM을 즉시 종료한다.
     *
     * <p>throw가 아닌 halt를 쓰는 이유: throw는 Spring이 rollback을 유발해 장애 상태를 재현할 수 없다.
     */
    public void hit(String currentPoint) {
        if (haltPoint.isBlank() || !haltPoint.equals(currentPoint)) return;
        log.warn("[fault-hook] HIT point={}. halt(1) 실행", currentPoint);
        Runtime.getRuntime().halt(1);
    }

    /**
     * 지정 point에서 sleepSeconds 동안 블록한다.
     *
     * <p>Besu 노드를 수동으로 kill할 시간을 확보하는 용도 (B-2 시나리오).
     */
    public void sleep(String currentPoint) {
        if (sleepPoint.isBlank() || !sleepPoint.equals(currentPoint)) return;
        log.warn(
                "[fault-hook] SLEEP {}s at point={}. 지금 docker stop besu-nodeX 실행하세요",
                sleepSeconds,
                currentPoint);
        try {
            Thread.sleep(sleepSeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.warn("[fault-hook] SLEEP 종료 at point={}", currentPoint);
    }
}
