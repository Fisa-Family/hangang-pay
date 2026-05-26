package family.fisa.hangangpay.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.response.PaymentExecutionResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.internal.PaymentExecutionPreparationResult;
import family.fisa.hangangpay.domain.transaction.internal.PaymentIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.PaymentIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.PaymentRateLimiter;
import family.fisa.hangangpay.domain.transaction.internal.PaymentRequestHashGenerator;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PaymentExecutionStateWriterTest {

    private static final Long USER_ID = 1L;
    private static final Long USER_PARTY_ID = 10L;
    private static final Long MERCHANT_PARTY_ID = 20L;
    private static final Long TRANSACTION_ID = 123L;

    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String REQUEST_HASH = "server-generated-request-hash";

    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PaymentIdempotencyStore paymentIdempotencyStore;
    @Mock private PaymentRateLimiter paymentRateLimiter;
    @Mock private PaymentRequestHashGenerator paymentRequestHashGenerator;

    private PaymentExecutionStateWriter paymentExecutionStateWriter;

    @BeforeEach
    void setUp() {
        paymentExecutionStateWriter =
                new PaymentExecutionStateWriter(
                        transactionRepository,
                        userRepository,
                        merchantRepository,
                        passwordEncoder,
                        paymentIdempotencyStore,
                        paymentRateLimiter,
                        paymentRequestHashGenerator);
    }

    @Test
    @DisplayName("동일 요청 재시도 시 기존 snapshot을 반환한다")
    void prepareExecution_sameRequestReturnsSnapshot() {
        Transaction transaction = paymentTransaction(TransactionStatus.SUCCESS);
        PaymentExecutionResponse snapshot =
                new PaymentExecutionResponse(
                        TRANSACTION_UUID,
                        TransactionStatus.SUCCESS,
                        "APV-2026-00000123",
                        "0x-snapshot",
                        new BigDecimal("10000"),
                        "성수 한강카페",
                        LocalDateTime.of(2026, 5, 25, 10, 0));

        given(transactionRepository.findByTransactionUuid(TRANSACTION_UUID))
                .willReturn(Optional.of(transaction));
        given(userRepository.findByIdWithParty(USER_ID)).willReturn(Optional.of(user()));
        given(passwordEncoder.matches("123456", "pin-hash")).willReturn(true);
        given(paymentRequestHashGenerator.generatePaymentExecuteHash(transaction))
                .willReturn(REQUEST_HASH);
        given(
                        paymentIdempotencyStore.beginExecution(
                                TRANSACTION_UUID, REQUEST_HASH, TRANSACTION_ID))
                .willReturn(PaymentIdempotencyDecision.returnSnapshot(snapshot));

        PaymentExecutionPreparationResult result =
                paymentExecutionStateWriter.prepareExecution(
                        USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456");

        assertThat(result.hasSnapshot()).isTrue();
        assertThat(result.responseSnapshot()).isSameAs(snapshot);
        assertThat(result.prepared()).isNull();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);

        verify(paymentRateLimiter, never()).checkExecutionRateLimit(any(), any(), any());
        verify(paymentRateLimiter, never()).checkBankOutboundRateLimit();
    }

    @Test
    @DisplayName("다른 requestHash면 IDEMPOTENCY_CONFLICT 예외가 발생한다")
    void prepareExecution_differentRequestHashThrowsConflict() {
        Transaction transaction = paymentTransaction(TransactionStatus.PENDING);

        givenExecutionBase(transaction);
        given(
                        paymentIdempotencyStore.beginExecution(
                                TRANSACTION_UUID, REQUEST_HASH, TRANSACTION_ID))
                .willReturn(PaymentIdempotencyDecision.conflict());

        assertThatThrownBy(
                        () ->
                                paymentExecutionStateWriter.prepareExecution(
                                        USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", TransactionErrorCode.IDEMPOTENCY_CONFLICT);

        verify(paymentRateLimiter, never()).checkExecutionRateLimit(any(), any(), any());
        verify(paymentRateLimiter, never()).checkBankOutboundRateLimit();
    }

    @Test
    @DisplayName("처리 중인 중복 요청은 PAYMENT_ALREADY_PROCESSING 예외가 발생한다")
    void prepareExecution_processingDuplicateThrowsAlreadyProcessing() {
        Transaction transaction = paymentTransaction(TransactionStatus.PENDING);

        givenExecutionBase(transaction);
        given(
                        paymentIdempotencyStore.beginExecution(
                                TRANSACTION_UUID, REQUEST_HASH, TRANSACTION_ID))
                .willReturn(PaymentIdempotencyDecision.processing());

        assertThatThrownBy(
                        () ->
                                paymentExecutionStateWriter.prepareExecution(
                                        USER_ID, USER_PARTY_ID, TRANSACTION_UUID, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_ALREADY_PROCESSING);

        verify(paymentRateLimiter, never()).checkExecutionRateLimit(any(), any(), any());
        verify(paymentRateLimiter, never()).checkBankOutboundRateLimit();
    }

    private void givenExecutionBase(Transaction transaction) {
        given(transactionRepository.findByTransactionUuid(TRANSACTION_UUID))
                .willReturn(Optional.of(transaction));
        given(userRepository.findByIdWithParty(USER_ID)).willReturn(Optional.of(user()));
        given(passwordEncoder.matches("123456", "pin-hash")).willReturn(true);
        given(paymentRequestHashGenerator.generatePaymentExecuteHash(transaction))
                .willReturn(REQUEST_HASH);
    }

    private Transaction paymentTransaction(TransactionStatus status) {
        Party userParty = party(USER_PARTY_ID, PartyType.USER);
        Party merchantParty = party(MERCHANT_PARTY_ID, PartyType.MERCHANT);

        return Transaction.builder()
                .id(TRANSACTION_ID)
                .transactionUuid(TRANSACTION_UUID)
                .transactionType(TransactionType.PAYMENT)
                .status(status)
                .fromParty(userParty)
                .toParty(merchantParty)
                .fromWallet(wallet(1L, userParty, "0x-user"))
                .toWallet(wallet(2L, merchantParty, "0x-merchant"))
                .amount(new BigDecimal("10000"))
                .approvalNumber("APV-2026-00000123")
                .itemName("아메리카노")
                .build();
    }

    private User user() {
        return User.builder()
                .id(USER_ID)
                .party(party(USER_PARTY_ID, PartyType.USER))
                .username("홍길동")
                .passwordHash("password-hash")
                .paymentPinHash("pin-hash")
                .phoneNumber("01012345678")
                .build();
    }

    private Wallet wallet(Long id, Party party, String address) {
        return Wallet.builder().id(id).party(party).address(address).build();
    }

    private Party party(Long id, PartyType type) {
        return Party.builder().id(id).partyType(type).build();
    }
}
