package family.fisa.hangangpay.domain.payment.repository;

import family.fisa.hangangpay.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {}
