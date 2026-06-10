package family.fisa.hangangpaybank.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchain.code.error.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.request.CancelRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ChargeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeResponse;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionCommandServiceTest {

    private static final String FROM_ADDRESS = "0x000000000000000000000000000000000000aaaa";
    private static final String TO_ADDRESS = "0x000000000000000000000000000000000000bbbb";
    private static final BigDecimal AMOUNT = new BigDecimal("100");

    @Mock private ChargeStateWriter chargeStateWriter;
    @Mock private PaymentStateWriter paymentStateWriter;
    @Mock private PaymentExecutionService paymentExecutionService;

    private TransactionCommandService service;

    @BeforeEach
    void setUp() {
        service =
                new TransactionCommandService(
                        chargeStateWriter, paymentStateWriter, paymentExecutionService);
    }

    @Test
    @DisplayName("충전 성공: 신규 요청을 선점한 뒤 charge state writer 결과를 그대로 반환한다")
    void charge_success_returnsWriterResult() {
        ChargeRequest request =
                new ChargeRequest(
                        "uuid-charge",
                        1L,
                        "1002-123-456789",
                        TO_ADDRESS,
                        new BigDecimal("9000"),
                        new BigDecimal("10000"));
        ChargeResponse writerResponse =
                new ChargeResponse(
                        "uuid-charge",
                        10L,
                        "0x-charge",
                        100L,
                        LocalDateTime.now(),
                        new BigDecimal("12345"));
        given(chargeStateWriter.findExistingCharge("uuid-charge")).willReturn(Optional.empty());
        given(chargeStateWriter.claimPendingCharge(request)).willReturn(20L);
        given(chargeStateWriter.executeCharge(request, 20L)).willReturn(writerResponse);

        ChargeResponse response = service.charge(request);

        assertThat(response).isEqualTo(writerResponse);
        verify(chargeStateWriter).validateChargeRequest(request);
    }

    @Test
    @DisplayName("충전 비즈니스 실패: 선점한 ledger를 FAILED로 바꾸고 FAILED ledger를 저장한다")
    void charge_businessFailure_savesFailedLedgers() {
        ChargeRequest request =
                new ChargeRequest(
                        "uuid-charge-failed",
                        1L,
                        "1002-123-456789",
                        TO_ADDRESS,
                        new BigDecimal("9000"),
                        new BigDecimal("10000"));
        BusinessException failure =
                new BusinessException(BlockchainErrorCode.BLOCKCHAIN_ISSUANCE_LIMIT_EXCEEDED);
        given(chargeStateWriter.findExistingCharge("uuid-charge-failed"))
                .willReturn(Optional.empty());
        given(chargeStateWriter.claimPendingCharge(request)).willReturn(30L);
        given(chargeStateWriter.executeCharge(request, 30L)).willThrow(failure);

        assertThatThrownBy(() -> service.charge(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_ISSUANCE_LIMIT_EXCEEDED);

        verify(chargeStateWriter).validateChargeRequest(request);
        verify(chargeStateWriter).markChargeFailed(30L, request.transactionUuid());
        verify(chargeStateWriter).saveFailedChargeLedgers(request);
    }

    @Test
    @DisplayName("충전 성공 재요청: 저장된 성공 응답을 그대로 반환한다")
    void charge_existingSuccess_returnsStoredResponse() {
        ChargeRequest request =
                new ChargeRequest(
                        "uuid-charge-success",
                        1L,
                        "1002-123-456789",
                        TO_ADDRESS,
                        new BigDecimal("9000"),
                        new BigDecimal("10000"));
        ChargeResponse existingResponse =
                new ChargeResponse(
                        "uuid-charge-success",
                        11L,
                        "0x-existing",
                        101L,
                        LocalDateTime.now(),
                        new BigDecimal("20000"));
        BlockchainLedger successLedger =
                BlockchainLedger.builder()
                        .id(11L)
                        .idempotentKey("uuid-charge-success")
                        .status(BlockchainTxStatus.SUCCESS)
                        .build();
        given(chargeStateWriter.findExistingCharge("uuid-charge-success"))
                .willReturn(Optional.of(successLedger));
        given(chargeStateWriter.getSuccessResponse(request, successLedger))
                .willReturn(existingResponse);

        ChargeResponse response = service.charge(request);

        assertThat(response).isEqualTo(existingResponse);
    }

    @Test
    @DisplayName("충전 처리 중 재요청: duplicate 예외를 반환하고 실패 ledger를 남기지 않는다")
    void charge_duplicateProcessing_throwsWithoutFailedLedger() {
        ChargeRequest request =
                new ChargeRequest(
                        "uuid-charge-pending",
                        1L,
                        "1002-123-456789",
                        TO_ADDRESS,
                        new BigDecimal("9000"),
                        new BigDecimal("10000"));
        BusinessException duplicate =
                new BusinessException(TransactionErrorCode.TRANSACTION_DUPLICATE_PROCESSING);
        BlockchainLedger pendingLedger =
                BlockchainLedger.builder()
                        .id(12L)
                        .idempotentKey("uuid-charge-pending")
                        .status(BlockchainTxStatus.PENDING)
                        .build();
        given(chargeStateWriter.findExistingCharge("uuid-charge-pending"))
                .willReturn(Optional.of(pendingLedger));
        willThrow(duplicate)
                .given(chargeStateWriter)
                .throwDuplicateProcessing("uuid-charge-pending");

        assertThatThrownBy(() -> service.charge(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(TransactionErrorCode.TRANSACTION_DUPLICATE_PROCESSING);
    }

    @Test
    @DisplayName("결제 성공: 실행 서비스 결과를 그대로 반환한다")
    void payment_success_returnsExecutionResult() {
        PaymentResponse executionResponse =
                PaymentResponse.from(
                        "uuid-1",
                        family.fisa.hangangpaybank.domain.ledger.entity.WalletLedgerStatus.SUCCESS,
                        LocalDateTime.now(),
                        new BigDecimal("400"),
                        new BigDecimal("100"));
        given(paymentExecutionService.payment(paymentRequest("uuid-1")))
                .willReturn(executionResponse);

        PaymentResponse response = service.payment(paymentRequest("uuid-1"));

        assertThat(response).isEqualTo(executionResponse);
    }

    @Test
    @DisplayName("결제 비즈니스 실패: rollback 이후 주소 기반 FAILED ledger 저장")
    void payment_businessFailure_savesFailedLedgerByAddress() {
        BusinessException failure =
                new BusinessException(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);
        given(paymentExecutionService.payment(paymentRequest("uuid-2"))).willThrow(failure);

        assertThatThrownBy(() -> service.payment(paymentRequest("uuid-2")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);

        verify(paymentStateWriter)
                .saveFailedWalletLedgersByAddress(
                        eq(FROM_ADDRESS), eq(TO_ADDRESS), eq("uuid-2"), eq(AMOUNT));
    }

    @Test
    @DisplayName("취소 성공: 실행 서비스 결과를 그대로 반환한다")
    void cancel_success_returnsExecutionResult() {
        CancelResponse executionResponse =
                CancelResponse.from(
                        "uuid-6",
                        "orig-1",
                        family.fisa.hangangpaybank.domain.ledger.entity.WalletLedgerStatus.SUCCESS,
                        LocalDateTime.now(),
                        new BigDecimal("400"),
                        new BigDecimal("100"));
        given(paymentExecutionService.cancel(cancelRequest("uuid-6", "orig-1")))
                .willReturn(executionResponse);

        CancelResponse response = service.cancel(cancelRequest("uuid-6", "orig-1"));

        assertThat(response).isEqualTo(executionResponse);
    }

    @Test
    @DisplayName("취소 비즈니스 실패: rollback 이후 주소 기반 FAILED ledger 저장")
    void cancel_businessFailure_savesFailedLedgerByAddress() {
        BusinessException failure =
                new BusinessException(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);
        given(paymentExecutionService.cancel(cancelRequest("uuid-7", "orig-2"))).willThrow(failure);

        assertThatThrownBy(() -> service.cancel(cancelRequest("uuid-7", "orig-2")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);

        verify(paymentStateWriter)
                .saveFailedWalletLedgersByAddress(
                        eq(FROM_ADDRESS), eq(TO_ADDRESS), eq("uuid-7"), eq(AMOUNT));
    }

    private PaymentRequest paymentRequest(String uuid) {
        return new PaymentRequest(uuid, FROM_ADDRESS, TO_ADDRESS, AMOUNT);
    }

    private CancelRequest cancelRequest(String uuid, String originalUuid) {
        return new CancelRequest(uuid, originalUuid, FROM_ADDRESS, TO_ADDRESS, AMOUNT);
    }
}
