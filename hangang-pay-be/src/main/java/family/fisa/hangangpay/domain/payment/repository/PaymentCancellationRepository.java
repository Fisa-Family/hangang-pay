package family.fisa.hangangpay.domain.payment.repository;

import family.fisa.hangangpay.domain.payment.entity.PaymentCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentCancellationRepository extends JpaRepository<PaymentCancellation, Long> {}
