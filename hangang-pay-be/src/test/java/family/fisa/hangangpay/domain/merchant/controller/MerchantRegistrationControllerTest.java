package family.fisa.hangangpay.domain.merchant.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import family.fisa.hangangpay.domain.merchant.code.error.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.dto.BusinessInfoResponse;
import family.fisa.hangangpay.domain.merchant.service.BusinessInfoQueryService;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MerchantRegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MerchantRegistrationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private BusinessInfoQueryService businessInfoQueryService;

    @Test
    @DisplayName("사업자 정보를 조회한다")
    void getBusinessInfo() throws Exception {
        given(businessInfoQueryService.getBusinessInfo("123-45-67890"))
                .willReturn(
                        new BusinessInfoResponse(
                                "123-45-67890", "성수 한강카페", "김한강", "서울 성동구 왕십리로 125", "카페"));

        mockMvc.perform(
                        get("/api/v1/merchant/business-info")
                                .param("businessNumber", "123-45-67890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.code").value("COMMON_OK"))
                .andExpect(jsonPath("$.result.businessNumber").value("123-45-67890"))
                .andExpect(jsonPath("$.result.merchantName").value("성수 한강카페"))
                .andExpect(jsonPath("$.result.ownerName").value("김한강"))
                .andExpect(jsonPath("$.result.address").value("서울 성동구 왕십리로 125"))
                .andExpect(jsonPath("$.result.businessType").value("카페"));
    }

    @Test
    @DisplayName("사업자 정보를 찾을 수 없으면 실패 응답을 반환한다")
    void getBusinessInfoNotFound() throws Exception {
        given(businessInfoQueryService.getBusinessInfo("000-00-00000"))
                .willThrow(new BusinessException(MerchantErrorCode.BUSINESS_INFO_NOT_FOUND));

        mockMvc.perform(
                        get("/api/v1/merchant/business-info")
                                .param("businessNumber", "000-00-00000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value("BUSINESS_INFO_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("사업자 정보를 찾을 수 없습니다"));
    }
}
