package family.fisa.hangangpay.domain.user.controller;

import family.fisa.hangangpay.domain.transfer.dto.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transfer.service.FundTransferService;
import family.fisa.hangangpay.domain.user.code.UserSuccessCode;
import family.fisa.hangangpay.domain.user.dto.UserProfileResponse;
import family.fisa.hangangpay.domain.user.service.UserService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FundTransferService fundTransferService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @SessionAttribute("userId") Long userId) {
        UserProfileResponse response = userService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    @GetMapping("/charges")
    @Operation(summary = "사용자 충전 내역 조회", description = "커서 기반으로 로그인한 사용자의 충전 내역을 조회합니다.")
    public ResponseEntity<ApiResponse<CursorPageResponse<ChargeHistoryItem>>> getChargeHistories(
            @SessionAttribute("userId") Long userId, CursorPageRequest request) {
        CursorPageResponse<ChargeHistoryItem> response =
                fundTransferService.getChargeHistories(userId, request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(UserSuccessCode.CHARGE_HISTORIES_RETRIEVED, response));
    }
}
