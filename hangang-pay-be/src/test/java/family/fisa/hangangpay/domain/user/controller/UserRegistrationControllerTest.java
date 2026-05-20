package family.fisa.hangangpay.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import family.fisa.hangangpay.domain.user.dto.UserRegisterResponse;
import family.fisa.hangangpay.domain.user.service.UserCommandService;
import family.fisa.hangangpay.global.exception.handler.GlobalExceptionHandler;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserRegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserRegistrationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserCommandService userCommandService;

    @Test
    @DisplayName("소비자 회원가입을 완료한다")
    void register() throws Exception {
        given(userCommandService.register(any(), any(HttpSession.class)))
                .willReturn(new UserRegisterResponse(10L, 20L));

        mockMvc.perform(
                        post("/api/v1/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "birthDate": "1990-07-30",
                                          "phoneNumber": "010-1234-5678",
                                          "password": "abc123!@",
                                          "paymentPin": "123456",
                                          "institutionId": 1,
                                          "accountNumber": "1002123456789",
                                          "termsAgreed": {
                                            "serviceTerms": true,
                                            "privacyTerms": true,
                                            "electronicFinanceTerms": true,
                                            "localCurrencyTerms": true
                                          }
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.code").value("USER_REGISTERED"))
                .andExpect(jsonPath("$.result.partyId").value(10))
                .andExpect(jsonPath("$.result.userId").value(20));
    }
}
