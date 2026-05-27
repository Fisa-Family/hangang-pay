package family.fisa.hangangpay.domain.transaction.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.ChargeResponse;
import family.fisa.hangangpay.domain.transaction.dto.request.ChargeExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.response.ChargeExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.ChargeExecutionPreparationResult;
import family.fisa.hangangpay.domain.transaction.internal.ChargeExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.internal.ChargeIdempotencyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeCommandService {

    private final BankClient bankClient;
    private final ChargeIdempotencyStore chargeIdempotencyStore;
    private final ChargeExecutionWriter chargeExecutionWriter;

    /** 충전 실행 오케스트레이션: 멱등성 판단 → 은행 충전 요청 → 상태 전환 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChargeExecuteResponse execute(Long sessionPartyId, ChargeExecuteRequest request) {

        ChargeExecutionPreparationResult result =
                chargeExecutionWriter.prepareProcessing(
                        sessionPartyId,
                        request.accountId(),
                        request.amount(),
                        request.transactionUuid(),
                        request.paymentPin());

        if (result.hasSnapshot()) {
            return result.responseSnapshot();
        }

        ChargeExecutionPrepared prepared = result.prepared();

        // 은행 충전 요청
        ChargeResponse bankResponse;
        try {
            log.info("충전 은행 요청 시작. transactionUuid={}", prepared.transactionUuid());
            bankResponse = bankClient.charge(prepared.toBankChargeRequest());
            log.info("충전 은행 요청 완료. transactionUuid={}", prepared.transactionUuid());
        } catch (ResourceAccessException ex) {
            log.error("충전 은행 연동 실패. transactionUuid={}", prepared.transactionUuid(), ex);
            ChargeExecuteResponse response =
                    chargeExecutionWriter.markUnknown(prepared.transactionUuid());
            chargeIdempotencyStore.markExecutionStatus(
                    prepared.transactionUuid(), TransactionStatus.UNKNOWN);
            return response;
        }

        // 충전 성공 처리
        ChargeExecuteResponse response =
                chargeExecutionWriter.completeSuccess(
                        prepared.transactionUuid(),
                        bankResponse.txHash(),
                        String.valueOf(bankResponse.bankTransactionId()),
                        bankResponse.confirmedAt());

        chargeIdempotencyStore.completeExecution(prepared.transactionUuid(), response);

        return response;
    }
}
