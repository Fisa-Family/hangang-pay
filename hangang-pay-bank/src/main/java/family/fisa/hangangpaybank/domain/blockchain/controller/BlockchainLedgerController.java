package family.fisa.hangangpaybank.domain.blockchain.controller;

import family.fisa.hangangpaybank.domain.blockchain.code.BlockchainSuccessCode;
import family.fisa.hangangpaybank.domain.blockchain.dto.response.BlockchainLedgerResponse;
import family.fisa.hangangpaybank.domain.blockchain.service.BlockchainLedgerQueryService;
import family.fisa.hangangpaybank.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BlockchainLedger", description = "블록체인 거래 조회 API")
@RestController
@RequestMapping("/api/v1/blockchain-ledgers")
@RequiredArgsConstructor
public class BlockchainLedgerController {

    private final BlockchainLedgerQueryService blockchainLedgerQueryService;

    @Operation(summary = "블록체인 거래 단건 조회 (txHash)", description = "트랜잭션 해시로 블록체인 거래 정보를 조회한다.")
    @GetMapping("/tx-hash/{txHash}")
    public ResponseEntity<ApiResponse<BlockchainLedgerResponse>> getByTxHash(
            @PathVariable String txHash) {
        // 1. txHash로 블록체인 거래 조회
        BlockchainLedgerResponse response = blockchainLedgerQueryService.getByTxHash(txHash);

        // 2. 성공 응답 반환
        return ResponseEntity.status(BlockchainSuccessCode.BLOCKCHAIN_LEDGER_DETAIL_OK.getStatus())
                .body(
                        ApiResponse.onSuccess(
                                BlockchainSuccessCode.BLOCKCHAIN_LEDGER_DETAIL_OK, response));
    }
}
