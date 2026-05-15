package family.fisa.hangangpay.domain.account.controller;

import family.fisa.hangangpay.domain.account.dto.AccountAddRequest;
import family.fisa.hangangpay.domain.account.dto.AccountListResponse;
import family.fisa.hangangpay.domain.account.dto.AccountResponse;
import family.fisa.hangangpay.domain.account.dto.PrimaryAccountResponse;
import family.fisa.hangangpay.domain.account.service.AccountService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 계좌 관련 HTTP 요청 처리 컨트롤러 */
@Tag(name = "계좌", description = "계좌 관리 API")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    /** 계좌 서비스 */
    private final AccountService accountService;

    /** ACCOUNT-001 등록 계좌 목록 조회 엔드포인트 */
    @Operation(summary = "등록 계좌 목록 조회", description = "현재 로그인한 사용자의 등록된 계좌 목록을 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 필요")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<AccountListResponse>> getAccounts(
            // TODO: 로그인 구현 후 HttpSession session 파라미터로 교체 및 아래 세션 인증 블록 주석 해제
            @RequestParam Long partyId) {

        // TODO: 로그인 구현 후 아래 세션 인증 블록 주석 해제
        // Long partyId = (Long) session.getAttribute("partyId");
        // if (partyId == null) {
        //     throw new BusinessException(GeneralErrorCode.UNAUTHORIZED_401);
        // }

        // 계좌 목록 조회 후 응답 반환
        AccountListResponse response = accountService.getAccounts(partyId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.OK, response));
    }

    /** ACCOUNT-002 계좌 추가 엔드포인트 */
    @Operation(summary = "계좌 추가", description = "은행 원장 확인 및 예금주 검증 후 계좌를 등록합니다. 최대 3개까지 등록 가능합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "계좌 추가 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "최대 계좌 수 초과 또는 중복 계좌"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "예금주 불일치"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 계좌")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> addAccount(
            // TODO: 로그인 구현 후 HttpSession session 파라미터로 교체 및 아래 세션 인증 블록 주석 해제
            @RequestParam Long partyId, @Valid @RequestBody AccountAddRequest request) {

        // TODO: 로그인 구현 후 아래 세션 인증 블록 주석 해제
        // Long partyId = (Long) session.getAttribute("partyId");
        // if (partyId == null) {
        //     throw new BusinessException(GeneralErrorCode.UNAUTHORIZED_401);
        // }

        // 계좌 추가 후 201 응답 반환
        AccountResponse response = accountService.addAccount(partyId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(GeneralSuccessCode.CREATED, response));
    }

    /** ACCOUNT-003 계좌 삭제 엔드포인트 */
    @Operation(summary = "계좌 삭제", description = "본인 계좌를 삭제합니다. 주거래 계좌와 마지막 계좌는 삭제할 수 없습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "주거래 계좌 삭제 시도 또는 마지막 계좌 삭제 시도"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "계좌 없음 또는 본인 계좌 아님")
    })
    @DeleteMapping("/{accountId}")
    public ResponseEntity<ApiResponse<?>> deleteAccount(
            // TODO: 로그인 구현 후 HttpSession session 파라미터로 교체 및 아래 세션 인증 블록 주석 해제
            @RequestParam Long partyId, @PathVariable Long accountId) {

        // TODO: 로그인 구현 후 아래 세션 인증 블록 주석 해제
        // Long partyId = (Long) session.getAttribute("partyId");
        // if (partyId == null) {
        //     throw new BusinessException(GeneralErrorCode.UNAUTHORIZED_401);
        // }

        // 계좌 삭제 후 200 응답 반환
        accountService.deleteAccount(partyId, accountId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.OK));
    }

    /** ACCOUNT-004 주거래 계좌 변경 엔드포인트 */
    @Operation(
            summary = "주거래 계좌 변경",
            description = "지정한 계좌를 주거래 계좌로 변경합니다. 기존 주거래 계좌는 일반 계좌로 전환됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 계좌 또는 본인 계좌 아님")
    })
    @PatchMapping("/{accountId}/primary")
    public ResponseEntity<ApiResponse<PrimaryAccountResponse>> changePrimaryAccount(
            // TODO: 로그인 구현 후 HttpSession session 파라미터로 교체 및 아래 세션 인증 블록 주석 해제
            @RequestParam Long partyId, @PathVariable Long accountId) {

        // TODO: 로그인 구현 후 아래 세션 인증 블록 주석 해제
        // Long partyId = (Long) session.getAttribute("partyId");
        // if (partyId == null) {
        //     throw new BusinessException(GeneralErrorCode.UNAUTHORIZED_401);
        // }

        // 주거래 계좌 변경 후 200 응답 반환
        PrimaryAccountResponse response = accountService.changePrimaryAccount(partyId, accountId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.OK, response));
    }
}
