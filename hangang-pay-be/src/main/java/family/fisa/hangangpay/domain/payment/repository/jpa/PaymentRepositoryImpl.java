package family.fisa.hangangpay.domain.payment.repository.jpa;

import family.fisa.hangangpay.domain.payment.entity.Payment;
import family.fisa.hangangpay.domain.payment.entity.PaymentStatus;
import family.fisa.hangangpay.domain.payment.repository.PaymentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Window<Payment> findPaymentHistory(
            Long partyId, List<PaymentStatus> statuses, Limit limit, ScrollPosition position) {
        return paymentJpaRepository.findByPayerParty_IdAndStatusInOrderByCreatedAtDescIdDesc(
                partyId, statuses, limit, position);
    }
}
