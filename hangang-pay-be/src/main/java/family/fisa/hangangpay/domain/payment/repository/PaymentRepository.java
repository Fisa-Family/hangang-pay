package family.fisa.hangangpay.domain.payment.repository;

import family.fisa.hangangpay.domain.payment.entity.Payment;
import family.fisa.hangangpay.domain.payment.entity.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;

public interface PaymentRepository {

    Window<Payment> findPaymentHistory(
            Long partyId, List<PaymentStatus> statuses, Limit limit, ScrollPosition position);

    Optional<Payment> findByIdWithPayerParty(Long paymentId);
}
