package family.fisa.hangangpay.domain.user.controller;

import static family.fisa.hangangpay.domain.user.dto.UserHistoryType.*;

import family.fisa.hangangpay.domain.payment.dto.response.PaymentHistoryItem;
import family.fisa.hangangpay.domain.payment.service.PaymentQueryService;
import family.fisa.hangangpay.domain.transfer.dto.ChargeHistoryItem;
import family.fisa.hangangpay.domain.transfer.dto.ExchangeHistoryItem;
import family.fisa.hangangpay.domain.transfer.service.FundTransferService;
import family.fisa.hangangpay.domain.user.dto.UserHistoryResponse;
import family.fisa.hangangpay.domain.user.dto.UserHistoryType;
import family.fisa.hangangpay.domain.user.dto.UserProfileResponse;
import family.fisa.hangangpay.domain.user.service.UserQueryService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;
import family.fisa.hangangpay.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@Tag(name = "마이페이지", description = "소비자 프로필 및 결제 내역 조회")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryService userQueryService;
    private final PaymentQueryService paymentQueryService;
    private final FundTransferService fundTransferService;

    @Operation(summary = "프로필 조회 (MY-001)", description = "로그인한 소비자의 닉네임, 지역, 가입일을 반환한다.")
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @SessionAttribute("userId") Long userId) {
        UserProfileResponse response = userQueryService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    @Operation(summary = "소비자 내역 조회 (MY-002)", description = "결제, 충전, 환전에 대한 모든 조회를 한번에 처리한다.")
    @GetMapping("/histories")
    public ResponseEntity<ApiResponse<UserHistoryResponse<?>>> getHistories(
            @SessionAttribute("userId") Long userId,
            @SessionAttribute("partyId") Long partyId,
            @RequestParam UserHistoryType historyType,
            CursorPageRequest cursor,
            @RequestParam(defaultValue = "20") int size) {

        UserHistoryResponse<?> result =
                switch (historyType) {
                    case PAYMENT -> {
                        CursorPageResponse<PaymentHistoryItem> page =
                                paymentQueryService.getUserPaymentHistory(partyId, cursor, size);
                        yield UserHistoryResponse.of(
                                PAYMENT,
                                page.content(),
                                page.hasNext(),
                                page.nextCursorCreatedAt(),
                                page.nextCursorId());
                    }
                    case CHARGE -> {
                        CursorPageResponse<ChargeHistoryItem> page =
                                fundTransferService.getChargeHistories(userId, cursor, size);
                        yield UserHistoryResponse.of(
                                CHARGE,
                                page.content(),
                                page.hasNext(),
                                page.nextCursorCreatedAt(),
                                page.nextCursorId());
                    }
                    case EXCHANGE -> {
                        CursorPageResponse<ExchangeHistoryItem> page =
                                fundTransferService.getExchangeHistories(userId, cursor, size);
                        yield UserHistoryResponse.of(
                                EXCHANGE,
                                page.content(),
                                page.hasNext(),
                                page.nextCursorCreatedAt(),
                                page.nextCursorId());
                    }
                };
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, result));
    }
}
