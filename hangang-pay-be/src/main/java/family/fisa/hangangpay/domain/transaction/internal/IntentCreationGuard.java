package family.fisa.hangangpay.domain.transaction.internal;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 의도(intent) 중복 생성 가드 — best-effort 부하 제어.
 *
 * <p><b>정확성 장치가 아니다.</b> 더블클릭·연타·매크로가 짧은 창에 PENDING intent를 다량 생성하는 것을 입구에서 컷한다. 이중 실행 방지는 Redis
 * 게이트·claim·DB UNIQUE의 몫이며 이 가드와 무관하다.
 *
 * <p>키 {@code intent:guard:{txType}:{digest}}, {@code SET NX} + TTL 3초.
 *
 * <p><b>의도된 트레이드오프(오탐):</b> 같은 사용자가 같은 내용으로 3초 내 재요청하면 정당한 요청도 차단된다. 부하 제어 목적상 수용하며, TTL이 짧아 손상이
 * 작다. TTL을 넘긴 지연 요청은 통과하지만(미탐) PENDING 하나 추가는 "다량"이 아니므로 무해하다.
 *
 * <p><b>fail-open:</b> Redis 장애 시 가드를 통과시킨다. 부하 제어 장치이므로 가용성이 우선이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentCreationGuard {

    private static final String KEY_PREFIX = "intent:guard:";
    private static final Duration TTL = Duration.ofSeconds(3);

    private final StringRedisTemplate redisTemplate;
    private final RequestHashDigest requestHashDigest;

    /**
     * 짧은 창 내 동일 의도 재요청이면 {@code INTENT_DUPLICATE_REQUEST}(429)로 차단한다.
     *
     * @param txType 거래 타입 (키 분리 + 다이제스트 재료)
     * @param partyId 요청 주체
     * @param amount 금액
     * @param extra 의도를 구분하는 추가 재료(충전의 accountId 등). 없으면 비운다.
     */
    public void check(TransactionType txType, Long partyId, BigDecimal amount, Object... extra) {
        String key = KEY_PREFIX + txType + ":" + digest(txType, partyId, amount, extra);
        try {
            Boolean created = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL);
            if (!Boolean.TRUE.equals(created)) {
                log.warn("의도 중복 생성 차단. txType={}, partyId={}", txType, partyId);
                throw new BusinessException(TransactionErrorCode.INTENT_DUPLICATE_REQUEST);
            }
        } catch (BusinessException e) {
            throw e; // 차단 결정은 그대로 전파
        } catch (RuntimeException e) {
            // fail-open: Redis 장애 시 통과시킨다 (부하 제어 장치, 가용성 우선)
            log.warn("의도 중복 가드 Redis 오류 - fail-open 통과. txType={}, partyId={}", txType, partyId, e);
        }
    }

    private String digest(
            TransactionType txType, Long partyId, BigDecimal amount, Object... extra) {
        Object[] parts = new Object[3 + extra.length];
        parts[0] = txType;
        parts[1] = partyId;
        parts[2] = amount;
        System.arraycopy(extra, 0, parts, 3, extra.length);
        return requestHashDigest.digest(parts);
    }
}
