package family.fisa.hangangpay.domain.merchant.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import family.fisa.hangangpay.domain.account.service.AccountCommandService;
import family.fisa.hangangpay.domain.merchant.dto.MerchantPaymentDetailResponse;
import family.fisa.hangangpay.domain.merchant.service.MerchantQrService;
import family.fisa.hangangpay.domain.merchant.service.MerchantQueryService;
import family.fisa.hangangpay.domain.transaction.dto.response.MerchantPaymentDetail;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.domain.transaction.service.ExchangeCommandService;
import family.fisa.hangangpay.domain.transaction.service.TransactionQueryService;
import family.fisa.hangangpay.global.exception.handler.GlobalExceptionHandler;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MerchantController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MerchantControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MerchantQrService qrService;
    @MockitoBean private MerchantQueryService merchantQueryService;
    @MockitoBean private AccountCommandService accountCommandService;
    @MockitoBean private TransactionQueryService transactionQueryService;
    @MockitoBean private ExchangeCommandService exchangeCommandService;

    @Test
    @DisplayName("가맹점 결제 상세 조회는 PAYMENT/CANCEL 타입과 detail을 함께 반환한다")
    void getMerchantPaymentDetail() throws Exception {
        MerchantPaymentDetail detail =
                new MerchantPaymentDetail(
                        25L,
                        new BigDecimal("12000"),
                        "김*영",
                        "APV-2026-00000025",
                        "SUCCESS",
                        LocalDateTime.of(2026, 5, 14, 14, 23),
                        false);

        given(transactionQueryService.getMerchantPaymentDetail(10L, 25L))
                .willReturn(MerchantPaymentDetailResponse.of(TransactionType.CANCEL, detail));

        mockMvc.perform(
                        get("/api/v1/merchant/payments/25")
                                .sessionAttr(SessionAttributeNames.PARTY_ID, 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.transactionType").value("CANCEL"))
                .andExpect(jsonPath("$.result.detail.transactionId").value(25))
                .andExpect(jsonPath("$.result.detail.payerName").value("김*영"))
                .andExpect(jsonPath("$.result.detail.approvalNumber").value("APV-2026-00000025"))
                .andExpect(jsonPath("$.result.detail.cancelAvailable").value(false))
                .andExpect(jsonPath("$.result.detail.transactionType").doesNotExist());
    }
}
