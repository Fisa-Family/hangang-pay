package family.fisa.hangangpay.domain.transaction.infra.redis.payment;

import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIntentDedupStore;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisPaymentIntentDedupStore implements PaymentIntentDedupStore {

    private static final String KEY_PREFIX = "payment:intent:dedup:";
    private static final Duration DEDUP_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<String> reserve(String fingerprint, String newTransactionUuid) {
        String key = KEY_PREFIX + fingerprint;

        Boolean created =
                redisTemplate.opsForValue().setIfAbsent(key, newTransactionUuid, DEDUP_TTL);

        if (Boolean.TRUE.equals(created)) {
            return Optional.empty();
        }

        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }
}
