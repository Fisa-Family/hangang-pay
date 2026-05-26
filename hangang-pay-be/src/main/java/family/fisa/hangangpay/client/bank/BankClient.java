package family.fisa.hangangpay.client.bank;

import family.fisa.hangangpay.client.bank.dto.*;

public interface BankClient {

    // bank_account 관련
    BankAccountResponse createBankAccount(CreateBankAccountRequest request);

    BankAccountResponse getBankAccount(Long institutionId, String accountNumber);

    // bank_wallet 관련 (Custodial)
    BankWalletResponse createBankWallet(CreateBankWalletRequest request);

    BankWalletResponse getBankWalletByAddress(String address);

    // 거래 시, Bank 관련
    BankTransactionStatusResponse getTransactionStatus(String transactionUuid);

    // 거래
    ChargeResponse charge(ChargeRequest request);

    ExchangeResponse exchange(ExchangeRequest request);

    PaymentResponse payment(PaymentRequest request);

    CancelResponse cancel(CancelRequest request);

    // blockchain
    BlockchainLedgerResponse getBlockchainLedgerByTxHash(String txHash);
}
