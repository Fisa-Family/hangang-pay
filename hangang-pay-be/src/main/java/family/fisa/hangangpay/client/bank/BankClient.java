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
import java.util.List;

public interface BankClient {

    // institution 관련
    List<InstitutionResponse> getInstitutions();

    InstitutionResponse getInstitution(Long id);

    // bank_account 관련
    BankAccountResponse createBankAccount(CreateBankAccountRequest request);

    BankAccountResponse getBankAccount(Long institutionId, String accountNumber);

    // bank_wallet 관련 (Custodial)
    BankWalletResponse createBankWallet(CreateBankWalletRequest request);

    BankWalletResponse getBankWalletByAddress(String address);

    // 거래
    ChargeResponse charge(ChargeRequest request);

    ExchangeResponse exchange(ExchangeRequest request);

    PaymentResponse payment(PaymentRequest request);

    CancelResponse cancel(CancelRequest request);

    // blockchain
    BlockchainLedgerResponse getBlockchainLedgerByTxHash(String txHash);
}
