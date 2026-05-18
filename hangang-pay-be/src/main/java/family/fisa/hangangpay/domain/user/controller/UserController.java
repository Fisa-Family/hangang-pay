package family.fisa.hangangpay.domain.user.controller;

import family.fisa.hangangpay.domain.payment.dto.response.UserPaymentHistoryItem;
import family.fisa.hangangpay.domain.payment.service.PaymentQueryService;
import family.fisa.hangangpay.domain.user.dto.UserProfileResponse;
import family.fisa.hangangpay.domain.user.service.UserQueryService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "마이페이지", description = "소비자 프로필 및 결제 내역 조회")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryService userQueryService;
    private final PaymentQueryService paymentQueryService;

    @Operation(summary = "프로필 조회 (MY-001)", description = "로그인한 소비자의 닉네임, 지역, 가입일을 반환한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음 (USER404_0)")
    @GetMapping("/profile")
    public ResponseEntity<family.fisa.hangangpay.global.response.ApiResponse<UserProfileResponse>>
            getProfile(@SessionAttribute("userId") Long userId) {
        UserProfileResponse response = userQueryService.getProfile(userId);
        return ResponseEntity.ok(
                family.fisa.hangangpay.global.response.ApiResponse.onSuccess(
                        GeneralSuccessCode.COMMON_OK, response));
    }

    @Operation(
            summary = "소비자 결제 내역 조회 (MY-002)",
            description = "커서 기반 페이지네이션으로 최신순 결제 내역을 반환한다. 첫 요청은 cursorCreatedAt/cursorId 없이 호출한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공 (내역 없으면 빈 리스트 반환)")
    @GetMapping("/payments")
    public ResponseEntity<
                    family.fisa.hangangpay.global.response.ApiResponse<
                            CursorPageResponse<UserPaymentHistoryItem>>>
            getPaymentHistory(
                    @SessionAttribute("partyId") Long partyId,
                    CursorPageRequest request,
                    @RequestParam(defaultValue = "20") int size) {
        CursorPageResponse<UserPaymentHistoryItem> response =
                paymentQueryService.getUserPaymentHistory(partyId, request, size);

        return ResponseEntity.ok(
                family.fisa.hangangpay.global.response.ApiResponse.onSuccess(
                        GeneralSuccessCode.COMMON_OK, response));
    }
}
