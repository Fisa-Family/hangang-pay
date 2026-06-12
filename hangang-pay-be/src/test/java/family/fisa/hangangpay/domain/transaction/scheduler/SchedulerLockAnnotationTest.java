package family.fisa.hangangpay.domain.transaction.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;

class SchedulerLockAnnotationTest {

    @Test
    void 모든_스케줄러_메서드에_SchedulerLock이_정확히_설정된다() throws NoSuchMethodException {
        assertLock(ReconcileScheduler.class, "reconcileExchanges", "reconcileExchanges");
        assertLock(ReconcileScheduler.class, "expireStaleIntents", "expireStaleIntents");
        assertLock(
                PaymentIntentExpiryScheduler.class,
                "expireStaleIntents",
                "expireStalePaymentIntents");
        assertLock(
                UnknownPaymentRecoveryScheduler.class,
                "resolveUnknownPayments",
                "resolveUnknownPayments");
        assertLock(
                UnknownPaymentRecoveryScheduler.class,
                "resolveUnknownCancels",
                "resolveUnknownCancels");
    }

    private void assertLock(Class<?> type, String method, String expectedName)
            throws NoSuchMethodException {
        Method m = type.getDeclaredMethod(method);
        SchedulerLock lock = m.getAnnotation(SchedulerLock.class);
        assertThat(lock).as("@SchedulerLock on %s.%s", type.getSimpleName(), method).isNotNull();
        assertThat(lock.name()).isEqualTo(expectedName);
        assertThat(lock.lockAtMostFor()).isEqualTo("5m");
        assertThat(lock.lockAtLeastFor()).isEqualTo("5s");
    }
}
