package family.fisa.hangangpay.domain.user.controller;

import family.fisa.hangangpay.domain.payment.dto.response.UserPaymentHistoryItem;
import family.fisa.hangangpay.domain.payment.service.PaymentQueryService;
import family.fisa.hangangpay.domain.user.dto.UserProfileResponse;
import family.fisa.hangangpay.domain.user.service.UserQueryService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.response.ApiResponse;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import io.swagger.v3.oas.annotations.Operation;
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
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @SessionAttribute(SessionAttributeNames.USER_ID) Long userId) {
        UserProfileResponse response = userQueryService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    @Operation(
            summary = "소비자 결제 내역 조회 (MY-002)",
            description = "커서 기반 페이지네이션으로 최신순 결제 내역을 반환한다. 첫 요청은 cursorCreatedAt/cursorId 없이 호출한다.")
    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<CursorPageResponse<UserPaymentHistoryItem>>>
            getPaymentHistory(
                    @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId,
                    CursorPageRequest request,
                    @RequestParam(defaultValue = "20") int size) {
        CursorPageResponse<UserPaymentHistoryItem> response =
                paymentQueryService.getUserPaymentHistory(partyId, request, size);

        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }
}
