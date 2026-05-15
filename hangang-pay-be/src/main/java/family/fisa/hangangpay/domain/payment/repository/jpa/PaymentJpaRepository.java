package family.fisa.hangangpay.domain.payment.repository.jpa;

import family.fisa.hangangpay.domain.payment.entity.Payment;
import family.fisa.hangangpay.domain.payment.entity.PaymentStatus;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"payeeParty"})
    Window<Payment> findByPayerParty_IdAndStatusInOrderByCreatedAtDescIdDesc(
            Long partyId, List<PaymentStatus> statuses, Limit limit, ScrollPosition position);
}
