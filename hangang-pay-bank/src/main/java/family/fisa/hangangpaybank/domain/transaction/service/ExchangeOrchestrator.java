package family.fisa.hangangpaybank.domain.transaction.service;

import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeOrchestrator {

    /** ERC20 기본 decimals (1e18) - BigDecimal 금액을 컨트랙트 단위로 변환할 때 사용 */
    private static final BigInteger TOKEN_DECIMALS = BigInteger.TEN.pow(18);

    private final ExchangeStateWriter stateWriter;
    private final ContractCallService contractCallService;

    public ExchangeResponse exchange(ExchangeRequest request) {
        log.info(
                "[bank] exchange 시작. transactionUuid={}, institutionId={}, amount={}",
                request.transactionUuid(),
                request.institutionId(),
                request.amount());

        // 1. PENDING 슬록 선점 (REQUIRES_NEW)
        Long ledgerId = stateWriter.claimExchange(request);

        // 2. 컨트렉트 호출
        TransactionReceipt receipt;
        try {
            receipt =
                    contractCallService.refund(
                            request.institutionId(),
                            request.walletAddress(),
                            toTokenUnit(request.amount()));
        } catch (Exception e) {
            log.error(
                    "[bank] exchange 컨트렉트 실패. transactionUuid={}, ledgerId={}",
                    request.transactionUuid(),
                    ledgerId,
                    e);
            stateWriter.failExchange(ledgerId, request);
            throw new BusinessException(TransactionErrorCode.EXCHANGE_CONTRACT_FAILED);
        }

        // 3. 성공 확정 (REQUIRES_NEW)
        ExchangeResponse response = stateWriter.completeExchange(ledgerId, request, receipt);

        log.info(
                "[bank] exchange 완료. transactionUuid={}, ledgerId={}, txHash={}",
                request.transactionUuid(),
                ledgerId,
                response.txHash());

        return response;
    }

    private static BigInteger toTokenUnit(BigDecimal amount) {
        return amount.multiply(new BigDecimal(TOKEN_DECIMALS)).toBigInteger();
    }
}
