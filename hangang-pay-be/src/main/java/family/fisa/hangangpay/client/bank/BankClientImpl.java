package family.fisa.hangangpay.client.bank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.client.bank.code.ExternalBankErrorCode;
import family.fisa.hangangpay.client.bank.dto.BankAccountResponse;
import family.fisa.hangangpay.client.bank.dto.BankTransactionStatusResponse;
import family.fisa.hangangpay.client.bank.dto.BankWalletResponse;
import family.fisa.hangangpay.client.bank.dto.BlockchainLedgerResponse;
import family.fisa.hangangpay.client.bank.dto.CancelRequest;
import family.fisa.hangangpay.client.bank.dto.CancelResponse;
import family.fisa.hangangpay.client.bank.dto.ChargeRequest;
import family.fisa.hangangpay.client.bank.dto.ChargeResponse;
import family.fisa.hangangpay.client.bank.dto.CreateBankAccountRequest;
import family.fisa.hangangpay.client.bank.dto.CreateBankWalletRequest;
import family.fisa.hangangpay.client.bank.dto.ExchangeRequest;
import family.fisa.hangangpay.client.bank.dto.ExchangeResponse;
import family.fisa.hangangpay.client.bank.dto.ExchangeStatusResponse;
import family.fisa.hangangpay.client.bank.dto.PaymentRequest;
import family.fisa.hangangpay.client.bank.dto.PaymentResponse;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.response.ApiResponse;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankClientImpl implements BankClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Bank 서버의 에러 코드를 비즈니스 에러 코드로 매핑 */
    private static final Map<String, BaseErrorCode> BANK_ERROR_MAPPINGS =
            Map.of(
                    AccountErrorCode.BANK_ACCOUNT_NOT_FOUND.getCode(),
                    AccountErrorCode.BANK_ACCOUNT_NOT_FOUND);

    private final RestClient bankRestClient;

    @Override
    public BankAccountResponse createBankAccount(CreateBankAccountRequest request) {
        // 1. 은행에 사용자 계좌 등록 요청
        ApiResponse<BankAccountResponse> response =
                callBank(
                        () ->
                                bankRestClient
                                        .post()
                                        .uri("/api/v1/bank-accounts")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public BankAccountResponse getBankAccount(Long institutionId, String accountNumber) {
        // 1. 은행에서 계좌 정보를 조회 (path variable + query parameter)
        ApiResponse<BankAccountResponse> response =
                callBank(
                        () ->
                                bankRestClient
                                        .get()
                                        .uri(
                                                uriBuilder ->
                                                        uriBuilder
                                                                .path(
                                                                        "/api/v1/bank-accounts/{accountNumber}")
                                                                .queryParam(
                                                                        "institutionId",
                                                                        institutionId)
                                                                .build(accountNumber))
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public BankWalletResponse createBankWallet(CreateBankWalletRequest request) {
        // 1. 은행에 사용자 지갑 발급 요청 (Custodial, bank가 keypair 생성)
        ApiResponse<BankWalletResponse> response =
                callBank(
                        () ->
                                bankRestClient
                                        .post()
                                        .uri("/api/v1/bank-wallets")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public BankWalletResponse getBankWalletByAddress(String address) {
        // 1. 은행에서 지갑 정보를 조회
        ApiResponse<BankWalletResponse> response =
                callBank(
                        () ->
                                bankRestClient
                                        .get()
                                        .uri("/api/v1/bank-wallets/address/{address}", address)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public BankTransactionStatusResponse getTransactionStatus(String transactionUuid) {
        // 1. Bank 결제 상태 조회 (GET /api/v1/transactions/{uuid}/status)
        ApiResponse<BankTransactionStatusResponse> response =
                callBank(
                        () ->
                                bankRestClient
                                        .get()
                                        .uri(
                                                "/api/v1/transactions/{transactionUuid}/payment/status",
                                                transactionUuid)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public ChargeResponse charge(ChargeRequest request) {
        // 1. 은행에 충전 요청 (계좌 → 토큰 mint)
        ApiResponse<ChargeResponse> response =
                callBank(
                        () ->
                                bankRestClient
                                        .post()
                                        .uri("/api/v1/transactions/charge")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public ExchangeResponse exchange(ExchangeRequest request) {
        // 1. 은행에 환전 요청 (토큰 burn → 계좌 입금)
        ApiResponse<ExchangeResponse> response =
                callBank(
                        () ->
                                bankRestClient
                                        .post()
                                        .uri("/api/v1/transactions/exchange")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public PaymentResponse payment(PaymentRequest request) {
        // 1. 은행에 결제 요청 (지갑 → 지갑 transfer)
        ApiResponse<PaymentResponse> response =
                callBank(
                        () ->
                                bankRestClient
                                        .post()
                                        .uri("/api/v1/transactions/payment")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public CancelResponse cancel(CancelRequest request) {
        // 1. 은행에 결제 취소 요청 (역방향 transfer)
        ApiResponse<CancelResponse> response =
                callBank(
                        () ->
                                bankRestClient
                                        .post()
                                        .uri("/api/v1/transactions/cancel")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public Optional<ExchangeStatusResponse> queryExchangeStatus(String transactionUuid) {
        log.info("bank 환전 상태 조회 호출. transactionUuid={}", transactionUuid);

        // 1. bank /api/v1/transactions/{uuid}/status 호출. 404는 Optional.empty()
        try {
            ApiResponse<ExchangeStatusResponse> response =
                    callBank(
                            () ->
                                    bankRestClient
                                            .get()
                                            .uri(
                                                    "/api/v1/transactions/{transactionUuid}/status",
                                                    transactionUuid)
                                            .retrieve()
                                            .body(new ParameterizedTypeReference<>() {}));

            // 2. 응답이 있으면 두 ledger 모두 있다는 의미
            ExchangeStatusResponse result = response.getResult();
            log.info(
                    "bank 환전 상태 조회 완료(존재). transactionUuid={}, bankTransactionId={}, txHash={}",
                    transactionUuid,
                    result.bankTransactionId(),
                    result.txHash());
            return Optional.of(result);
        } catch (RestClientResponseException ex) {
            // 3. 404만 빈 응답으로 흡수. 그 외 5xx 등은 그대로 전파 (reconcile에서 재시도 대상)
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("bank 환전 상태 조회: 거래 없음(NOT_FOUND). transactionUuid={}", transactionUuid);
                return Optional.empty();
            }
            throw ex;
        }
    }

    @Override
    public void localMint(Long institutionId, String walletAddress, BigDecimal amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("institutionId", institutionId);
        body.put("walletAddress", walletAddress);
        body.put("amount", amount);
        bankRestClient
                .post()
                .uri("/api/v1/internal/local/mint")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public BlockchainLedgerResponse getBlockchainLedgerByTxHash(String txHash) {
        // 1. 은행에서 블록체인 거래 정보 조회
        ApiResponse<BlockchainLedgerResponse> response =
                callBank(
                        () ->
                                bankRestClient
                                        .get()
                                        .uri("/api/v1/blockchain-ledgers/tx-hash/{txHash}", txHash)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    /**
     * 매핑된 에러 코드가 없다면 RestClientResponseException 에러를 그대로 반환 -> GlobalExceptionHandler에서
     * BANK_SERVER_ERROR로 처리
     */
    private <T> ApiResponse<T> callBank(Supplier<ApiResponse<T>> request) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            throw mapBankError(ex).map(BusinessException::new).orElseThrow(() -> ex);
        }
    }

    /** Bank 서버의 에러를 서비스 서버의 에러 코드로 매핑 */
    private Optional<BaseErrorCode> mapBankError(RestClientResponseException ex) {
        try {
            BankErrorResponse response =
                    OBJECT_MAPPER.readValue(ex.getResponseBodyAsString(), BankErrorResponse.class);

            BaseErrorCode mapped = BANK_ERROR_MAPPINGS.get(response.code());
            if (mapped != null) {
                return Optional.of(mapped);
            }

            if (response.code() != null && response.code().startsWith("BLOCKCHAIN_")) {
                return Optional.of(
                        new ExternalBankErrorCode(
                                HttpStatus.valueOf(ex.getStatusCode().value()),
                                response.code(),
                                response.message()));
            }

            return Optional.empty();
        } catch (JsonProcessingException parseException) {
            log.warn("Failed to parse bank error response. body={}", ex.getResponseBodyAsString());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BankErrorResponse(String code, String message) {}
}
