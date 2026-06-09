package family.fisa.hangangpaybank.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpaybank.domain.blockchain.code.error.BlockchainErrorCode;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequest;
import family.fisa.hangangpaybank.domain.blockchainoutbox.dto.BlockchainSyncRequestResult;
import family.fisa.hangangpaybank.domain.blockchainoutbox.port.BlockchainSyncRequester;
import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.BankWalletRepository;
import family.fisa.hangangpaybank.domain.transaction.dto.request.CancelRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;
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
class PaymentExecutionServiceTest {

    private static final String FROM_ADDRESS = "0x000000000000000000000000000000000000aaaa";
    private static final String TO_ADDRESS = "0x000000000000000000000000000000000000bbbb";
    private static final BigDecimal AMOUNT = new BigDecimal("100");

    @Mock private BankWalletRepository bankWalletRepository;
    @Mock private PaymentStateWriter paymentStateWriter;
    @Mock private BlockchainSyncRequester syncRequester;
    @Mock private ContractCallService contractCallService;

    private PaymentExecutionService service;

    @BeforeEach
    void setUp() {
        service =
                new PaymentExecutionService(
                        bankWalletRepository,
                        paymentStateWriter,
                        syncRequester,
                        contractCallService);
    }

    @Test
    @DisplayName("결제 성공: DB 잔액 차감/증가, WalletLedger(SUCCESS), syncRequester 호출")
    void payment_success_updatesDbBalancesAndRequestsSync() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO);

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(paymentStateWriter.findExisting("uuid-1")).willReturn(Optional.empty());
        given(contractCallService.isMerchant(TO_ADDRESS)).willReturn(true);
        given(
                        paymentStateWriter.saveSuccessWalletLedgers(
                                eq(from), eq(to), eq("uuid-1"), eq(AMOUNT)))
                .willReturn(LocalDateTime.now());
        given(syncRequester.request(any())).willReturn(new BlockchainSyncRequestResult(1L, 1L));

        PaymentResponse response = service.payment(paymentRequest("uuid-1"));

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("400"));
        assertThat(to.getBalance()).isEqualByComparingTo(new BigDecimal("100"));
        verify(syncRequester).request(any(BlockchainSyncRequest.class));
    }

    @Test
    @DisplayName("결제 성공: contractCallService.pay() 미호출 확인 (DB-First)")
    void payment_success_doesNotCallContractPay() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO);

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(paymentStateWriter.findExisting("uuid-2")).willReturn(Optional.empty());
        given(contractCallService.isMerchant(TO_ADDRESS)).willReturn(true);
        given(paymentStateWriter.saveSuccessWalletLedgers(any(), any(), any(), any()))
                .willReturn(LocalDateTime.now());
        given(syncRequester.request(any())).willReturn(new BlockchainSyncRequestResult(1L, 1L));

        service.payment(paymentRequest("uuid-2"));

        verify(contractCallService, never()).pay(any(), any(), any());
    }

    @Test
    @DisplayName("비가맹점 수신자: 비즈니스 예외 발생, 잔액 미변경")
    void payment_nonMerchant_throwsAndKeepsBalance() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));

        given(paymentStateWriter.findExisting("uuid-3")).willReturn(Optional.empty());
        given(contractCallService.isMerchant(TO_ADDRESS)).willReturn(false);

        assertThatThrownBy(() -> service.payment(paymentRequest("uuid-3")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);

        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("500"));
        verify(syncRequester, never()).request(any());
    }

    @Test
    @DisplayName("DB 잔액 부족: 비즈니스 예외 발생, 잔액 미변경")
    void payment_insufficientBalance_throwsAndKeepsBalance() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("50"));
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO);

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(paymentStateWriter.findExisting("uuid-4")).willReturn(Optional.empty());
        given(contractCallService.isMerchant(TO_ADDRESS)).willReturn(true);

        assertThatThrownBy(() -> service.payment(paymentRequest("uuid-4")))
                .isInstanceOf(BusinessException.class);

        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("50"));
        verify(syncRequester, never()).request(any());
    }

    @Test
    @DisplayName("SUCCESS 멱등성 재요청: syncRequester 재호출 없이 기존 응답 반환")
    void payment_idempotentSuccess_returnsExistingResultWithoutSync() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("400"));
        BankWallet to = wallet(TO_ADDRESS, new BigDecimal("100"));
        BlockchainLedger existing = successLedger("uuid-5");

        given(bankWalletRepository.findByWalletAddress(FROM_ADDRESS)).willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddress(TO_ADDRESS)).willReturn(Optional.of(to));
        given(paymentStateWriter.findExisting("uuid-5")).willReturn(Optional.of(existing));

        PaymentResponse response = service.payment(paymentRequest("uuid-5"));

        assertThat(response.status()).isEqualTo("SUCCESS");
        verify(syncRequester, never()).request(any());
        verify(contractCallService, never()).isMerchant(any());
    }

    @Test
    @DisplayName("취소 성공: WalletLedger(SUCCESS) 저장, syncRequester 호출")
    void cancel_success_savesLedgerAndRequestsSync() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));
        BankWallet to = wallet(TO_ADDRESS, BigDecimal.ZERO);

        given(bankWalletRepository.findByWalletAddressWithLock(FROM_ADDRESS))
                .willReturn(Optional.of(from));
        given(bankWalletRepository.findByWalletAddressWithLock(TO_ADDRESS))
                .willReturn(Optional.of(to));
        given(paymentStateWriter.findExisting("uuid-6")).willReturn(Optional.empty());
        given(contractCallService.isMerchant(FROM_ADDRESS)).willReturn(true);
        given(
                        paymentStateWriter.saveSuccessWalletLedgers(
                                eq(from), eq(to), eq("uuid-6"), eq(AMOUNT)))
                .willReturn(LocalDateTime.now());
        given(syncRequester.request(any())).willReturn(new BlockchainSyncRequestResult(1L, 1L));

        CancelResponse response = service.cancel(cancelRequest("uuid-6", "orig-1"));

        assertThat(response.transactionUuid()).isEqualTo("uuid-6");
        assertThat(response.originalTransactionUuid()).isEqualTo("orig-1");
        assertThat(response.status()).isEqualTo("SUCCESS");
        verify(syncRequester).request(any(BlockchainSyncRequest.class));
    }

    @Test
    @DisplayName("취소 비가맹점: 비즈니스 예외 발생, 잔액 미변경")
    void cancel_nonMerchant_throwsAndKeepsBalance() {
        BankWallet from = wallet(FROM_ADDRESS, new BigDecimal("500"));

        given(paymentStateWriter.findExisting("uuid-7")).willReturn(Optional.empty());
        given(contractCallService.isMerchant(FROM_ADDRESS)).willReturn(false);

        assertThatThrownBy(() -> service.cancel(cancelRequest("uuid-7", "orig-2")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BlockchainErrorCode.BLOCKCHAIN_MERCHANT_NOT_REGISTERED);

        assertThat(from.getBalance()).isEqualByComparingTo(new BigDecimal("500"));
    }

    private PaymentRequest paymentRequest(String uuid) {
        return new PaymentRequest(uuid, FROM_ADDRESS, TO_ADDRESS, AMOUNT);
    }

    private CancelRequest cancelRequest(String uuid, String originalUuid) {
        return new CancelRequest(uuid, originalUuid, FROM_ADDRESS, TO_ADDRESS, AMOUNT);
    }

    private static BankWallet wallet(String address, BigDecimal balance) {
        BankWallet wallet =
                BankWallet.builder()
                        .id(1L)
                        .institution(Institution.builder().id(1L).build())
                        .walletAddress(address)
                        .encryptedPrivateKey("encrypted")
                        .build();
        wallet.updateBalance(balance);
        return wallet;
    }

    private static BlockchainLedger successLedger(String uuid) {
        return BlockchainLedger.builder()
                .id(1L)
                .institution(Institution.builder().id(1L).build())
                .idempotentKey(uuid)
                .status(BlockchainTxStatus.SUCCESS)
                .confirmedAt(LocalDateTime.now().minusMinutes(1))
                .build();
    }
}
