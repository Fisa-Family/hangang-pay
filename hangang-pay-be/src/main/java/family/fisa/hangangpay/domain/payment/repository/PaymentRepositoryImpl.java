package family.fisa.hangangpay.domain.payment.repository;

import family.fisa.hangangpay.domain.payment.entity.Payment;
import family.fisa.hangangpay.domain.payment.entity.PaymentStatus;
import family.fisa.hangangpay.domain.payment.repository.jpa.PaymentJpaRepository;
import java.util.List;
import java.util.Optional;
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

    @Override
    public Optional<Payment> findByIdWithPayerParty(Long paymentId) {
        return paymentJpaRepository.findWithPayerPartyById(paymentId);
    }
}
