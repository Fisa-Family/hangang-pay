package family.fisa.hangangpaybank.domain.blockchain.controller;

import family.fisa.hangangpaybank.domain.blockchain.dto.request.LocalMintRequest;
import family.fisa.hangangpaybank.domain.blockchain.service.ContractCallService;
import java.math.BigDecimal;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로컬 seed 데이터와 온체인 상태 동기화용 컨트롤러 — local 프로파일 전용 */
@Slf4j
@Profile("local")
@RestController
@RequestMapping("/api/v1/internal/local")
@RequiredArgsConstructor
public class LocalSeedController {

    private static final BigDecimal TOKEN_DECIMALS = BigDecimal.TEN.pow(18);

    private final ContractCallService contractCallService;

    @PostMapping("/mint")
    public ResponseEntity<Void> mint(@RequestBody LocalMintRequest request) {
        BigInteger tokenUnits = request.amount().multiply(TOKEN_DECIMALS).toBigInteger();
        contractCallService.charge(request.institutionId(), request.walletAddress(), tokenUnits);
        log.info(
                "[local-seed] mint 완료. wallet={}, amount={}",
                request.walletAddress(),
                request.amount());
        return ResponseEntity.ok().build();
    }
}
