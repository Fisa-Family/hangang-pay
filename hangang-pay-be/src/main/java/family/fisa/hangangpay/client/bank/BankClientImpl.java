package family.fisa.hangangpay.client.bank;

import family.fisa.hangangpay.client.bank.dto.BankAccountResponse;
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
import family.fisa.hangangpay.client.bank.dto.InstitutionResponse;
import family.fisa.hangangpay.client.bank.dto.PaymentRequest;
import family.fisa.hangangpay.client.bank.dto.PaymentResponse;
import family.fisa.hangangpay.global.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class BankClientImpl implements BankClient {

    private final RestClient bankRestClient;

    @Override
    public List<InstitutionResponse> getInstitutions() {
        // 1. 은행에 기관 목록 조회 요청
        ApiResponse<List<InstitutionResponse>> response =
            bankRestClient
                .get()
                .uri("/api/v1/institutions")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public InstitutionResponse getInstitution(Long id) {
        // 1. 은행에 특정 기관 정보 조회 요청
        ApiResponse<InstitutionResponse> response =
            bankRestClient
                .get()
                .uri("/api/v1/institutions/{id}", id)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public BankAccountResponse createBankAccount(CreateBankAccountRequest request) {
        // 1. 은행에 사용자 계좌 등록 요청
        ApiResponse<BankAccountResponse> response =
            bankRestClient
                .post()
                .uri("/api/v1/bank-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public BankAccountResponse getBankAccount(Long institutionId, String accountNumber) {
        // 1. 은행에서 계좌 정보를 조회 (path variable + query parameter)
        ApiResponse<BankAccountResponse> response = bankRestClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .path("/api/v1/bank-accounts/{accountNumber}")
                    .queryParam("institutionId", institutionId)
                    .build(accountNumber))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public BankWalletResponse createBankWallet(CreateBankWalletRequest request) {
        // 1. 은행에 사용자 지갑 발급 요청 (Custodial, bank가 keypair 생성)
        ApiResponse<BankWalletResponse> response =
            bankRestClient
                .post()
                .uri("/api/v1/bank-wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public BankWalletResponse getBankWalletByAddress(String address) {
        // 1. 은행에서 지갑 정보를 조회
        ApiResponse<BankWalletResponse> response =
            bankRestClient
                .get()
                .uri("/api/v1/bank-wallets/address/{address}", address)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public ChargeResponse charge(ChargeRequest request) {
        // 1. 은행에 충전 요청 (계좌 → 토큰 mint)
        ApiResponse<ChargeResponse> response =
            bankRestClient
                .post()
                .uri("/api/v1/transactions/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public ExchangeResponse exchange(ExchangeRequest request) {
        // 1. 은행에 환전 요청 (토큰 burn → 계좌 입금)
        ApiResponse<ExchangeResponse> response =
            bankRestClient
                .post()
                .uri("/api/v1/transactions/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public PaymentResponse payment(PaymentRequest request) {
        // 1. 은행에 결제 요청 (지갑 → 지갑 transfer)
        ApiResponse<PaymentResponse> response =
            bankRestClient
                .post()
                .uri("/api/v1/transactions/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public CancelResponse cancel(CancelRequest request) {
        // 1. 은행에 결제 취소 요청 (역방향 transfer)
        ApiResponse<CancelResponse> response =
            bankRestClient
                .post()
                .uri("/api/v1/transactions/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public BlockchainLedgerResponse getBlockchainLedgerByTxHash(String txHash) {
        // 1. 은행에서 블록체인 거래 정보 조회
        ApiResponse<BlockchainLedgerResponse> response =
            bankRestClient
                .get()
                .uri("/api/v1/blockchain-ledgers/tx-hash/{txHash}", txHash)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        // 2. 응답에서 결과 추출
        return response.getResult();
    }
}
