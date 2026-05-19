package family.fisa.hangangpay.domain.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTx;
import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpay.domain.blockchain.entity.ReferenceType;
import family.fisa.hangangpay.domain.blockchain.repository.BlockchainTxRepository;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.payment.dto.response.UserPaymentHistoryDetail;
import family.fisa.hangangpay.domain.payment.entity.Payment;
import family.fisa.hangangpay.domain.payment.entity.PaymentStatus;
import family.fisa.hangangpay.domain.payment.repository.PaymentRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.pagination.PaginationService;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.util.Optional;
import javax.swing.text.html.Option;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    MerchantRepository merchantRepository;
    @Mock
    PaginationService paginationService;
    @Mock
    BlockchainTxRepository blockchainTxRepository;

    @InjectMocks
    PaymentQueryService paymentQueryService;

    private static final Long PARTY_ID = 10L;
    private static final Long PAYMENT_ID = 25L;

    private Payment payment(Long payerPartyId) {
        Party payerParty = Party.builder().id(payerPartyId).partyType(PartyType.USER).build();
        return Payment.builder()
                      .id(PAYMENT_ID)
                      .payerParty(payerParty)
                      .itemName("스타벅스 강남점")
                      .amount(new BigDecimal("12000"))
                      .approvalNumber("APV-2026-00000025")
                      .status(PaymentStatus.SUCCESS)
                      .build();
    }

    private BlockchainTx blockchainTx() {
        return BlockchainTx.builder()
                           .referenceId(PAYMENT_ID)
                           .referenceType(ReferenceType.PAYMENT)
                           .txHash("0xabc")
                           .status(BlockchainTxStatus.CONFIRMED)
                           .build();
    }

    @Nested
    @DisplayName("갤제 내역 상세 조회 (getUserPaymentHistoryDetail)")
    class PaymentDetail {

        @Test
        @DisplayName("정상: 본인 결제 + blockchain_tx 있음")
        void success_withBlockchainTx() throws Exception {
            // given
            when(paymentRepository.findByIdWithPayerParty(PAYMENT_ID))
                .thenReturn(Optional.of(payment(PARTY_ID)));
            when(blockchainTxRepository.findByReferenceTypeAndReferenceId(
                ReferenceType.PAYMENT,
                PAYMENT_ID))
                .thenReturn(Optional.of(blockchainTx()));
            // when
            UserPaymentHistoryDetail result =
                paymentQueryService.getUserPaymentHistoryDetail(PARTY_ID, PAYMENT_ID);

            // then
            assertThat(result.historyId()).isEqualTo(PAYMENT_ID);
            assertThat(result.itemName()).isEqualTo("스타벅스 강남점");
            assertThat(result.amount()).isEqualByComparingTo("12000");
            assertThat(result.approvalNumber()).isEqualTo("APV-2026-00000025");
            assertThat(result.paymentStatus()).isEqualTo("SUCCESS");
            assertThat(result.txHash()).isEqualTo("0xabc");
            assertThat(result.blockchainStatus()).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("정상: blockchain_tx 없음")
        void success_withoutBlockchainTx() throws Exception{
            // given
            when(paymentRepository.findByIdWithPayerParty(PAYMENT_ID))
                .thenReturn(Optional.of(payment(PARTY_ID)));
            when(blockchainTxRepository.findByReferenceTypeAndReferenceId(
                ReferenceType.PAYMENT,
                PAYMENT_ID))
                .thenReturn(Optional.empty());
            // when
            UserPaymentHistoryDetail result =
                paymentQueryService.getUserPaymentHistoryDetail(PARTY_ID, PAYMENT_ID);

            // then
            assertThat(result.historyId()).isEqualTo(PAYMENT_ID);
            assertThat(result.txHash()).isNull();
            assertThat(result.blockchainStatus()).isNull();
        }

        @Test
        @DisplayName("payment 가 없는 경우")
        void throws_whenPaymentNotFound() throws Exception{
            // given
            when(paymentRepository.findByIdWithPayerParty(PAYMENT_ID))
                .thenReturn(Optional.empty());

            // when, then
            assertThatThrownBy(
                () -> paymentQueryService.getUserPaymentHistoryDetail(PARTY_ID, PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", UserErrorCode.HISTORY_NOT_FOUND);
        }

        @Test
        @DisplayName("본인 결제가 아닌 경우")
        void throws_whenNotOwner() throws Exception{
            // given
            Long otherPartyId = 999L;
            when(paymentRepository.findByIdWithPayerParty(PAYMENT_ID))
                .thenReturn(Optional.of(payment(otherPartyId)));
            // when, then
            assertThatThrownBy(
                () -> paymentQueryService.getUserPaymentHistoryDetail(PARTY_ID, PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", UserErrorCode.NOT_OWNER);
        }
    }
}