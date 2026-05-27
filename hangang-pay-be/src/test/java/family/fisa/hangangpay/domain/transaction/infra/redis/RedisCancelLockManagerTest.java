package family.fisa.hangangpay.domain.transaction.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.infra.redis.cancel.RedisCancelLockManager;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisCancelLockManagerTest {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final String ORIGINAL_PAYMENT_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String KEY = "cancel:lock:" + ORIGINAL_PAYMENT_UUID;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisCancelLockManager redisCancelLockManager;

    @BeforeEach
    void setUp() {
        redisCancelLockManager = new RedisCancelLockManager(redisTemplate);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("lock 획득에 성공하면 supplier를 실행하고 lock을 해제한다")
    void withCancelLock_acquiredRunsSupplierAndReleasesLock() {
        // 1. setIfAbsent=true이면 현재 요청이 originalPaymentUuid lock을 선점한 것이다
        // - 취소는 cancelUuid가 아닌 originalPaymentUuid로 락을 잡는다
        // - cancelUuid는 CANCEL 레코드 생성 전에는 존재하지 않기 때문이다
        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(LOCK_TTL))).willReturn(true);

        // 2. 락 획득 후 supplier 실행
        String result =
                redisCancelLockManager.withCancelLock(ORIGINAL_PAYMENT_UUID, () -> "success");

        // 3. supplier 반환값이 그대로 전달된다
        assertThat(result).isEqualTo("success");

        // 4. 실행 후에는 token이 일치할 때만 삭제하는 Lua script로 lock을 해제한다
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(KEY)), anyString());
    }

    @Test
    @DisplayName("lock 획득에 실패하면 CANCEL_ALREADY_PROCESSING 예외가 발생한다")
    void withCancelLock_lockBusyThrowsAlreadyProcessing() {
        // 1. setIfAbsent=false이면 다른 요청이 이미 같은 originalPaymentUuid 취소를 처리 중이다
        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(LOCK_TTL))).willReturn(false);

        // 2. CANCEL_ALREADY_PROCESSING 예외가 발생해야 한다
        assertThatThrownBy(
                        () ->
                                redisCancelLockManager.withCancelLock(
                                        ORIGINAL_PAYMENT_UUID, () -> "should-not-run"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.CANCEL_ALREADY_PROCESSING);

        // 3. lock을 잡지 못했으므로 release도 시도하지 않는다
        verify(redisTemplate, never())
                .execute(any(RedisScript.class), any(List.class), anyString());
    }

    @Test
    @DisplayName("supplier에서 예외가 발생해도 lock은 해제한다")
    void withCancelLock_supplierThrowsStillReleasesLock() {
        // 1. lock 획득은 성공
        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(LOCK_TTL))).willReturn(true);

        // 2. Bank 호출 실패나 DB 오류가 나도 lock은 finally에서 반드시 해제되어야 한다
        // - lock을 해제하지 않으면 TTL(30s)이 만료될 때까지 동일 결제 취소가 막힌다
        assertThatThrownBy(
                        () ->
                                redisCancelLockManager.withCancelLock(
                                        ORIGINAL_PAYMENT_UUID,
                                        () -> {
                                            throw new IllegalArgumentException("bank timeout");
                                        }))
                .isInstanceOf(IllegalArgumentException.class);

        // 3. 예외 발생 후에도 Lua script로 lock을 해제한다
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(KEY)), anyString());
    }
}
