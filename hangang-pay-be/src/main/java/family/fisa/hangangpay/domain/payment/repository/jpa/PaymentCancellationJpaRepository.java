package family.fisa.hangangpay.domain.payment.repository.jpa;

import family.fisa.hangangpay.domain.payment.entity.PaymentCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentCancellationJpaRepository
        extends JpaRepository<PaymentCancellation, Long> {}
