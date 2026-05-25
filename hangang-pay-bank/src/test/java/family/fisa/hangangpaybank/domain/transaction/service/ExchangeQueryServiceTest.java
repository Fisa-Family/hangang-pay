package family.fisa.hangangpaybank.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpaybank.domain.blockchain.entity.BlockchainLedger;
import family.fisa.hangangpaybank.domain.blockchain.repository.BlockchainLedgerRepository;
import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.ledger.repository.AccountLedgerRepository;
import family.fisa.hangangpaybank.domain.transaction.code.error.TransactionErrorCode;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeStatusResponse;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeQueryServiceTest {

    private static final String UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Long BANK_TX_ID = 999L;
    private static final String TX_HASH = "0xabc123";

    @Mock AccountLedgerRepository accountLedgerRepository;
    @Mock BlockchainLedgerRepository blockchainLedgerRepository;

    @InjectMocks ExchangeQueryService exchangeQueryService;

    private AccountLedger accountLedger() {
        return AccountLedger.builder().id(BANK_TX_ID).idempotentKey(UUID).build();
    }

    private BlockchainLedger blockchainLedger() {
        return BlockchainLedger.builder().txHash(TX_HASH).idempotentKey(UUID).build();
    }

    @Test
    @DisplayName("두 ledger 모두 있으면 SUCCESS 응답 반환")
    void status_두_ledger_모두_존재() {
        // given
        when(accountLedgerRepository.findByIdempotentKey(UUID))
            .thenReturn(Optional.of(accountLedger()));
        when(blockchainLedgerRepository.findByIdempotentKey(UUID))
            .thenReturn(Optional.of(blockchainLedger()));

        // when
        ExchangeStatusResponse response = exchangeQueryService.getStatus(UUID);

        // then
        assertThat(response.transactionUuid()).isEqualTo(UUID);
        assertThat(response.bankTransactionId()).isEqualTo(BANK_TX_ID);
        assertThat(response.txHash()).isEqualTo(TX_HASH);
    }

    @Test
    @DisplayName("account_ledger 없으면 TRANSACTION_NOT_FOUND, blockchain_ledger는 조회되지 않음")
    void status_account_ledger_없음() {
        // given
        when(accountLedgerRepository.findByIdempotentKey(UUID)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> exchangeQueryService.getStatus(UUID))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(TransactionErrorCode.TRANSACTION_NOT_FOUND);

        verify(blockchainLedgerRepository, never()).findByIdempotentKey(any());
    }

    @Test
    @DisplayName("blockchain_ledger 없으면 TRANSACTION_NOT_FOUND (account_ledger는 있음)")
    void status_blockchain_ledger_없음() {
        // given
        when(accountLedgerRepository.findByIdempotentKey(UUID))
            .thenReturn(Optional.of(accountLedger()));
        when(blockchainLedgerRepository.findByIdempotentKey(UUID))
            .thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> exchangeQueryService.getStatus(UUID))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(TransactionErrorCode.TRANSACTION_NOT_FOUND);
    }
}