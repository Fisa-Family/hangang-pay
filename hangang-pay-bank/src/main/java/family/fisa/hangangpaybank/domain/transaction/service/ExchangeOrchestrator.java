package family.fisa.hangangpaybank.domain.transaction.service;

import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeOrchestrator {

    private final ExchangeStateWriter exchangeStateWriter;
    private final ExchangeExecutionService exchangeExecutionService;

    public ExchangeResponse exchange(ExchangeRequest request) {
        try {
            // 환전 실행 (현금 선입금 + account_ledger SUCCESS + outbox NEW)를 메인 트랜잭션에 위힘
            return exchangeExecutionService.exchange(request);
        } catch (BusinessException e) {
            log.warn(
                    "[bank] exchange 비즈니스 실패. transactionUuid={}, message={}",
                    request.transactionUuid(),
                    e.getMessage());

            // 실패 기록 (account_ledger FAILED)을 돌깁 트랜잭션으로 남긴다.
            // 보상 자체가 실패하더라고 원래 예외를 그대로 전파해야 하므로 별도 Try/catch로 감싼다
            try {
                exchangeStateWriter.saveFailedAccountLedger(request);
            } catch (Exception compensationError) {
                log.error(
                        "[bank] 실패 보상 기록 중 오류(원 예외는 그대로 전파). transactionUuid={}",
                        request.transactionUuid(),
                        compensationError);
            }

            throw e;
        }
    }
}
