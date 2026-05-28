package family.fisa.hangangpay.domain.transaction.infra.redis.payment;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentLockManager;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** 같은 transactionUuid 결제 실행 흐름이 동시에 두 개 들어가지 못하게 막는다. */
@Component
@RequiredArgsConstructor
public class RedisPaymentLockManager implements PaymentLockManager {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final String KEY_PREFIX = "payment:lock:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(
                    """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """,
                    Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public <T> T withTransactionLock(String transactionUuid, Supplier<T> supplier) {
        String key = key(transactionUuid);
        String token = UUID.randomUUID().toString();

        // setIfAbsent는 Redis SET NX 역할이다.
        // 같은 transactionUuid 실행은 하나만 lock을 선점할 수 있다.
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);

        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_ALREADY_PROCESSING);
        }

        try {
            return supplier.get();
        } finally {
            releaseLock(key, token);
        }
    }

    private String key(String transactionUuid) {
        return KEY_PREFIX + transactionUuid;
    }

    private void releaseLock(String key, String token) {
        // 다른 요청의 lock을 지우지 않도록 내가 저장한 token일 때만 삭제한다.
        // Redis 안에서 GET 비교와 DEL 삭제를 한 번에 실행해 중간 경합을 막는다.
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
    }
}
