package family.fisa.hangangpay.auth.controller;

import family.fisa.hangangpay.auth.code.success.AuthSuccessCode;
import family.fisa.hangangpay.auth.dto.UserRegisterRequest;
import family.fisa.hangangpay.auth.dto.UserRegisterResponse;
import family.fisa.hangangpay.auth.service.UserRegistrationService;
import family.fisa.hangangpay.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원가입", description = "소비자 회원가입 API")
@RestController
@RequestMapping("/api/v1/auth/users")
@RequiredArgsConstructor
public class RegistrationController {

    private final UserRegistrationService userRegistrationService;

    @Operation(summary = "소비자 회원가입 (REG-001)", description = "인증된 휴대폰과 계좌 정보로 소비자 회원가입을 완료한다.")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserRegisterResponse>> register(
            @Valid @RequestBody UserRegisterRequest request, HttpSession session) {
        UserRegisterResponse response = userRegistrationService.register(request, session);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(AuthSuccessCode.USER_REGISTERED, response));
    }
}
