package com.repayment.api.repayment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.repayment.domain.repayment.dto.response.RepaymentResultResponse;
import com.repayment.domain.repayment.exception.RepaymentExceedsRepayableAmountException;
import com.repayment.domain.repayment.service.RepaymentService;
import com.repayment.global.code.LoanStatus;
import com.repayment.global.code.RepaymentChannel;
import com.repayment.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("상환 API")
class RepaymentControllerTest {

    @Mock
    private RepaymentService repaymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Spring Boot처럼 날짜를 배열이 아닌 문자열(2026-10-21T10:15:30)로 내보낸다.
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(new RepaymentController(repaymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("대출 1에 30,000원을 상환하면 고객 상환 채널로 서비스를 부르고 200과 상환 결과 다섯 칸을 돌려준다")
    void 상환_결과를_돌려준다() throws Exception {
        // given
        given(repaymentService.repay(eq(1L), argThat(amount -> amount.compareTo(new BigDecimal("30000")) == 0),
                eq(RepaymentChannel.CUSTOMER)))
                .willReturn(RepaymentResultResponse.builder()
                        .paidAmount(new BigDecimal("30000"))
                        .paidAt(LocalDateTime.of(2026, 10, 21, 10, 15, 30))
                        .loanPrincipalBalance(new BigDecimal("9000000"))
                        .loanStatus(LoanStatus.NORMAL)
                        .remainingRepayableAmount(new BigDecimal("10000"))
                        .build());

        // when & then
        mockMvc.perform(post("/api/loans/1/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 30000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paidAmount").value(30000))
                .andExpect(jsonPath("$.data.paidAt").value("2026-10-21T10:15:30"))
                .andExpect(jsonPath("$.data.loanPrincipalBalance").value(9000000))
                .andExpect(jsonPath("$.data.loanStatus").value("NORMAL"))
                .andExpect(jsonPath("$.data.remainingRepayableAmount").value(10000));
    }

    @Test
    @DisplayName("상환 금액 없이 요청하면 400 INVALID_INPUT_VALUE와 amount 필드 오류를 돌려주고 서비스를 부르지 않는다")
    void 금액이_없으면_서비스를_부르지_않는다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/loans/1/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        then(repaymentService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("갚을 금액 5,000원보다 많이 내 서비스가 거절하면 422 REPAYMENT_EXCEEDS_REPAYABLE_AMOUNT와 data 5,000을 돌려준다")
    void 초과하면_갚을_금액을_data에_담는다() throws Exception {
        // given
        given(repaymentService.repay(eq(1L), any(), eq(RepaymentChannel.CUSTOMER)))
                .willThrow(new RepaymentExceedsRepayableAmountException(1L, new BigDecimal("5001"), new BigDecimal("5000")));

        // when & then
        mockMvc.perform(post("/api/loans/1/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 5001}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("REPAYMENT_EXCEEDS_REPAYABLE_AMOUNT"))
                .andExpect(jsonPath("$.data").value(5000));
    }
}
