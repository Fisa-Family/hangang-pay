package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.ExchangeResponse;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.request.ExchangeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ExchangeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.exchange.ExchangeRequestHashGenerator;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** EXCHANGE 명령 오케스트레이터 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeCommandService {

    /** PENDING 거래 임계 시간 - 초과 시 orphan으로 간주하고 reconcile 시도 */
    private static final int ORPHAN_THRESHOLD_MINUTES = 5;

    private final TransactionRepository transactionRepository;
    private final ExchangeQueryService exchangeQueryService;
    private final ExchangeStateWriter stateWriter;
    private final BankClient bankClient;
    private final ExchangeReconcileService exchangeReconcileService;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;

    private final ExchangeIdempotencyStore idempotencyStore;
    private final ExchangeRequestHashGenerator requestHashGenerator;

    /** 사용자 환전 실행 */
    public ExchangeExecuteResponse executeUserExchange(
            Long partyId, ExchangeExecuteRequest request) {
        log.info(
                "환전 실행 시작. partyId={}, transactionUuid={}, amount={}",
                partyId,
                request.transactionUuid(),
                request.amount());

        // 1. 핀번호 검증
        verifyUserPaymentPin(partyId, request.paymentPin());

        // 2. 멱등성 검증
        Optional<ExchangeExecuteResponse> idempotentHit = openIdempotencyGate(partyId, request);

        if (idempotentHit.isPresent()) {
            log.info("멱등 hit. partyId={}, transactionUuid={}", partyId, request.transactionUuid());
            return idempotentHit.get();
        }

        // 3. 환전 자격 검증 (마지막 충전 직후 잔액의 60% 이상 사용)
        verifyEligibility(partyId);

        // 4. 슬롯 선점: wallet 락 + inflight 체크 + PENDING 저장(별도 Tx)
        Long transactionId = stateWriter.claimExchange(partyId, request);

        return runBankExchange(partyId, request, transactionId);
    }

    /** 가맹점 환전 실행 */
    public ExchangeExecuteResponse executeMerchantExchange(
            Long partyId, ExchangeExecuteRequest request) {
        log.info(
                "환전 실행 시작(merchant). partyId={}, transactionUuid={}, amount={}",
                partyId,
                request.transactionUuid(),
                request.amount());

        // 1. 핀번호 검증
        verifyMerchantPaymentPin(partyId, request.paymentPin());

        // 2. 멱등 체크: 같은 transactionUuid가 이미 있으면 상태별 처리
        Optional<ExchangeExecuteResponse> idempotentHit = openIdempotencyGate(partyId, request);

        if (idempotentHit.isPresent()) {
            log.info("멱등 hit. partyId={}, transactionUuid={}", partyId, request.transactionUuid());
            return idempotentHit.get();
        }

        Long transactionId = stateWriter.claimSettlementExchange(partyId, request);
        return runBankExchange(partyId, request, transactionId);
    }

    /** claim 이후 bank 호출 로직 */
    private ExchangeExecuteResponse runBankExchange(
            Long partyId, ExchangeExecuteRequest request, Long transactionId) {
        // 1. 외부 호출: 락/Tx 밖에서 bank 호출
        ExchangeResponse bankResponse;
        try {
            bankResponse =
                    bankClient.exchange(stateWriter.buildBankRequest(transactionId, request));
        } catch (RuntimeException ex) {

            log.error(
                    "환전 실행 실패. partyId={}, transactionUuid={}",
                    partyId,
                    request.transactionUuid(),
                    ex);

            stateWriter.failExchange(transactionId);

            // 멱등 record FAILED 마킹
            idempotencyStore.failExecution(request.transactionUuid());
            throw ex;
        }

        // 2. 완료 마킹 (별도 Tx) + 응답 빌드
        ExchangeExecuteResponse response =
                stateWriter.completeExchange(
                        transactionId,
                        bankResponse.txHash(),
                        String.valueOf(bankResponse.bankTransactionId()));

        // 3. 성공 응답 snapshot 저장: 동일 transactionUuid 재시도 시 Bank 재호출 없이 그대로 반환
        idempotencyStore.completeExecution(request.transactionUuid(), response);

        log.info(
                "환전 실행 완료. partyId={}, transactionId={}, txHash={}",
                partyId,
                transactionId,
                bankResponse.txHash());

        return response;
    }

    /** Redis 멱등 게이트 첫 요청이면 Optional.empty() 진행을 알림 이미 처리됐고나 진행 중인 요청이면 분기 처리 */
    private Optional<ExchangeExecuteResponse> openIdempotencyGate(
            Long partyId, ExchangeExecuteRequest request) {
        // 1. 해시 값 생성
        // TODO 현재 null 반환
        String requestHash = requestHashGenerator.generate(partyId, request);

        ExchangeIdempotencyDecision decision =
                idempotencyStore.beginExecution(request.transactionUuid(), requestHash);

        return switch (decision.type()) {
            case NEW_REQUEST -> Optional.empty();
            case RETURN_SNAPSHOT -> Optional.of(decision.responseSnapshot());
            case ALREADY_FAILED ->
                    throw new BusinessException(TransactionErrorCode.EXCHANGE_ALREADY_FAILED);
            case PROCESSING ->
                    throw new BusinessException(TransactionErrorCode.EXCHANGE_IN_PROGRESS);
            case CONFLICT -> throw new BusinessException(TransactionErrorCode.IDEMPOTENCY_CONFLICT);
        };
    }

    /** 사용자 결제 PIN 검증 */
    private void verifyUserPaymentPin(Long partyId, String paymentPin) {
        User user =
                userRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (!user.matchesPaymentPin(paymentPin, passwordEncoder)) {
            log.warn("환전 PIN 불일치(user). partyId={}", partyId);
            throw new BusinessException(TransactionErrorCode.INVALID_PAYMENT_PIN);
        }
    }

    /** 가맹점 결제 PIN 검증 */
    private void verifyMerchantPaymentPin(Long partyId, String paymentPin) {
        Merchant merchant =
                merchantRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(
                                () -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

        if (!merchant.matchesPaymentPin(paymentPin, passwordEncoder)) {
            log.warn("환전 PIN 불일치(merchant). partyId={}", partyId);
            throw new BusinessException(TransactionErrorCode.INVALID_PAYMENT_PIN);
        }
    }

    /** 환전 자격 검증 */
    private void verifyEligibility(Long partyId) {
        if (!exchangeQueryService.checkEligibility(partyId)) {
            log.warn("환전 자격 미달. partyId={}", partyId);
            throw new BusinessException(TransactionErrorCode.EXCHANGE_NOT_ELIGIBLE);
        }
    }
}
