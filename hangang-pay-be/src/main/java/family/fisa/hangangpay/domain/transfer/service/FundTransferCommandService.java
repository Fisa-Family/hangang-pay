package family.fisa.hangangpay.domain.transfer.service;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTx;
import family.fisa.hangangpay.domain.blockchain.entity.BlockchainTxStatus;
import family.fisa.hangangpay.domain.blockchain.entity.ReferenceType;
import family.fisa.hangangpay.domain.blockchain.repository.BlockchainTxRepository;
import family.fisa.hangangpay.domain.institution.entity.BankAccount;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.BankAccountRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.transfer.code.error.ChargeErrorCode;
import family.fisa.hangangpay.domain.transfer.dto.ChargeExecuteRequest;
import family.fisa.hangangpay.domain.transfer.dto.ChargeReceiptResponse;
import family.fisa.hangangpay.domain.transfer.entity.FundTransfer;
import family.fisa.hangangpay.domain.transfer.entity.TransferStatus;
import family.fisa.hangangpay.domain.transfer.entity.TransferType;
import family.fisa.hangangpay.domain.transfer.repository.FundTransferRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/** 충전 실행 커맨드 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundTransferCommandService {

    private static final BigDecimal MONTHLY_LIMIT = new BigDecimal("1000000");
    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.10");

    private final PartyRepository partyRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final BankAccountRepository bankAccountRepository;
    private final FundTransferRepository fundTransferRepository;
    private final BlockchainTxRepository blockchainTxRepository;
    private final ChargeBlockchainService chargeBlockchainService;

    /** 충전 실행: 발행 가능량 검증 -> 계좌 차감 -> mint -> 상태 업데이트 */
    @Transactional
    public ChargeReceiptResponse executeCharge(Long partyId, ChargeExecuteRequest request) {
        BigDecimal chargeAmount = request.getAmount();

        // 1. 만원 단위 검증
        if (chargeAmount.remainder(new BigDecimal("10000")).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ChargeErrorCode.INVALID_UNIT);
        }

        // 2. 월 한도 검증
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = startOfMonth.plusMonths(1);
        BigDecimal usedAmount =
                fundTransferRepository.sumMonthlyAmount(
                        partyId,
                        TransferType.CHARGE,
                        TransferStatus.SUCCESS,
                        startOfMonth,
                        startOfNextMonth);
        if (usedAmount.add(chargeAmount).compareTo(MONTHLY_LIMIT) > 0) {
            throw new BusinessException(ChargeErrorCode.LIMIT_EXCEEDED);
        }

        // 3. Party 조회
        Party party =
                partyRepository
                        .findById(partyId)
                        .orElseThrow(() -> new BusinessException(GeneralErrorCode.COMMON_NOT_FOUND));

        // 4. 계좌 소유권 검증
        Account account =
                accountRepository
                        .findByIdAndParty_Id(request.getAccountId(), partyId)
                        .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        Institution bank = account.getInstitution();

        // 5. 지갑 조회
        Wallet wallet =
                walletRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(() -> new BusinessException(GeneralErrorCode.COMMON_NOT_FOUND));

        // TODO: 결제 PIN 검증 (paymentPin 필드 User 엔티티 추가 후 구현)

        // 6. 발행 가능량 검증 (CBDC 준비금 - DepositToken totalSupply)
        BigInteger cbdcReserve = chargeBlockchainService.getCbdcReserve(bank);
        BigInteger depositTokenSupply = chargeBlockchainService.getDepositTokenTotalSupply(bank);
        BigInteger issuable = cbdcReserve.subtract(depositTokenSupply);
        BigInteger chargeAmountInt = chargeAmount.toBigInteger();
        if (chargeAmountInt.compareTo(issuable) > 0) {
            log.warn(
                    "발행 가능량 초과: bank={}, issuable={}, requested={}",
                    bank.getInstitutionName(),
                    issuable,
                    chargeAmountInt);
            throw new BusinessException(ChargeErrorCode.ISSUABLE_EXCEEDED);
        }

        // 7. 할인 계산
        BigDecimal discountAmount = chargeAmount.multiply(DISCOUNT_RATE);
        BigDecimal finalAmount = chargeAmount.subtract(discountAmount);

        // 8. 계좌 잔액 검증 및 차감
        BankAccount bankAccount =
                bankAccountRepository
                        .findByInstitution_IdAndAccountNumber(
                                bank.getId(), account.getAccountNumber())
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.BANK_ACCOUNT_NOT_FOUND));
        if (bankAccount.getBalance().compareTo(finalAmount) < 0) {
            throw new BusinessException(ChargeErrorCode.INSUFFICIENT_BALANCE);
        }
        bankAccount.deductBalance(finalAmount);
        log.info(
                "계좌 차감: partyId={}, accountId={}, deducted={}, remainBalance={}",
                partyId,
                account.getId(),
                finalAmount,
                bankAccount.getBalance());

        // 9. FundTransfer 저장 (PENDING)
        FundTransfer fundTransfer =
                fundTransferRepository.save(
                        FundTransfer.builder()
                                .party(party)
                                .account(account)
                                .wallet(wallet)
                                .amount(chargeAmount)
                                .discountAmount(discountAmount)
                                .discountRate(DISCOUNT_RATE)
                                .status(TransferStatus.PENDING)
                                .transferType(TransferType.CHARGE)
                                .build());

        // 10. mint 호출 — 실패 시 MINT_FAILED → 트랜잭션 롤백
        log.info(
                "mint 호출: partyId={}, chargeId={}, to={}, amount={}",
                partyId,
                fundTransfer.getId(),
                wallet.getAddress(),
                chargeAmountInt);
        TransactionReceipt receipt =
                chargeBlockchainService.mint(bank, wallet.getAddress(), chargeAmountInt);

        // 11. mint 성공 — FundTransfer SUCCESS + BlockchainTx CONFIRMED
        fundTransfer.updateStatus(TransferStatus.SUCCESS);
        blockchainTxRepository.save(
                BlockchainTx.builder()
                        .referenceId(fundTransfer.getId())
                        .referenceType(ReferenceType.FUND_TRANSFER)
                        .txHash(receipt.getTransactionHash())
                        .status(BlockchainTxStatus.CONFIRMED)
                        .build());

        log.info(
                "충전 완료: partyId={}, chargeId={}, amount={}, finalAmount={}, txHash={}",
                partyId,
                fundTransfer.getId(),
                chargeAmount,
                finalAmount,
                receipt.getTransactionHash());

        return ChargeReceiptResponse.builder()
                .partyId(partyId)
                .chargeId(fundTransfer.getId())
                .amount(chargeAmount)
                .finalAmount(finalAmount)
                .chargedAt(fundTransfer.getCreatedAt())
                .build();
    }
}
