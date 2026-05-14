package family.fisa.hangangpay.domain.user.controller;

import family.fisa.hangangpay.domain.user.dto.UserProfileResponse;
import family.fisa.hangangpay.domain.user.service.UserService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.response.ApiResponse;
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

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @SessionAttribute("userId") Long userId) {
        UserProfileResponse response = userService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.OK, response));
    }
}
