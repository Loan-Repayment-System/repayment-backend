package com.repayment.api.loan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.repayment.domain.delinquency.dto.response.InstallmentDelinquencyChargeDetailResponse;
import com.repayment.domain.loan.dto.response.LoanAccountDetailResponse;
import com.repayment.domain.loan.dto.response.LoanBasicInfoResponse;
import com.repayment.domain.loan.dto.response.LoanCurrentStatusResponse;
import com.repayment.domain.loan.exception.LoanAccountNotFoundException;
import com.repayment.domain.loan.service.LoanAccountDetailService;
import com.repayment.domain.repayment.dto.response.RepayableAmountResponse;
import com.repayment.domain.repayment.dto.response.RepaymentTargetResponse;
import com.repayment.domain.schedule.dto.response.InstallmentDetailResponse;
import com.repayment.global.code.DelinquencyStatus;
import com.repayment.global.code.InterestType;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.LoanStatus;
import com.repayment.global.code.RepaymentScheduleStatus;
import com.repayment.global.code.RepaymentTargetType;
import com.repayment.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("대출 현황 조회 API")
class LoanAccountControllerTest {

    @Mock
    private LoanAccountDetailService loanAccountDetailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Spring Boot처럼 날짜를 배열이 아닌 문자열(2026-10-18)로 내보낸다.
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(new LoanAccountController(loanAccountDetailService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("연체 대출이면 200과 명세 이름의 loan·status·installments·repayableAmount를 돌려주고, 기한이익상실·만기 경과 묶음과 대상 id는 내보내지 않는다")
    void 연체_대출_응답() throws Exception {
        // given
        InstallmentDetailResponse installment = InstallmentDetailResponse.builder()
                .installmentNo(2)
                .status(RepaymentScheduleStatus.OVERDUE)
                .scheduledDate(LocalDate.of(2026, 10, 18))
                .delinquencyCharges(List.of(InstallmentDelinquencyChargeDetailResponse.builder()
                        .chargeDate(LocalDate.of(2026, 10, 19))
                        .delinquencyStatus(DelinquencyStatus.IN_PROGRESS)
                        .build()))
                .build();
        LoanAccountDetailResponse detail = LoanAccountDetailResponse.builder()
                .loan(LoanBasicInfoResponse.builder().loanAccountId(1L).interestType(InterestType.VARIABLE).build())
                .status(new LoanCurrentStatusResponse(LoanStatus.OVERDUE, LoanJudgmentStatus.INSTALLMENT_OVERDUE))
                .installments(List.of(installment))
                .repayableAmount(new RepayableAmountResponse(new BigDecimal("239"), List.of(
                        new RepaymentTargetResponse(1, RepaymentTargetType.INSTALLMENT_DELINQUENCY_CHARGE, 77L,
                                new BigDecimal("239")))))
                .build();
        given(loanAccountDetailService.getDetail(1L)).willReturn(detail);

        // when & then
        mockMvc.perform(get("/api/loans/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loan.loanAccountId").value(1))
                .andExpect(jsonPath("$.data.loan.interestType").value("VARIABLE"))
                .andExpect(jsonPath("$.data.status.loanStatus").value("OVERDUE"))
                .andExpect(jsonPath("$.data.status.latestJudgment").value("INSTALLMENT_OVERDUE"))
                .andExpect(jsonPath("$.data.installments[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$.data.installments[0].scheduledDate").value("2026-10-18"))
                .andExpect(jsonPath("$.data.installments[0].delinquencyCharges[0].delinquencyStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.repayableAmount.totalAmount").value(239))
                .andExpect(jsonPath("$.data.repayableAmount.targets[0].type").value("INSTALLMENT_DELINQUENCY_CHARGE"))
                .andExpect(jsonPath("$.data.repayableAmount.targets[0].targetId").doesNotExist())
                .andExpect(jsonPath("$.data.acceleration").doesNotExist())
                .andExpect(jsonPath("$.data.maturityOverdue").doesNotExist());
    }

    @Test
    @DisplayName("완제 대출이면 200과 loan·status만 돌려주고 installments·repayableAmount 키는 없다")
    void 완제_대출_응답() throws Exception {
        // given
        LoanAccountDetailResponse detail = LoanAccountDetailResponse.builder()
                .loan(LoanBasicInfoResponse.builder().loanAccountId(1L).build())
                .status(new LoanCurrentStatusResponse(LoanStatus.PAID_OFF, LoanJudgmentStatus.PAID_OFF))
                .build();
        given(loanAccountDetailService.getDetail(1L)).willReturn(detail);

        // when & then
        mockMvc.perform(get("/api/loans/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.loanStatus").value("PAID_OFF"))
                .andExpect(jsonPath("$.data.installments").doesNotExist())
                .andExpect(jsonPath("$.data.repayableAmount").doesNotExist());
    }

    @Test
    @DisplayName("없는 대출이면 404 LOAN_ACCOUNT_NOT_FOUND를 돌려준다")
    void 없는_대출은_404() throws Exception {
        // given
        given(loanAccountDetailService.getDetail(1L)).willThrow(new LoanAccountNotFoundException(1L));

        // when & then
        mockMvc.perform(get("/api/loans/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("대출 계좌 id가 숫자가 아닌 abc면 400 INVALID_INPUT_VALUE를 돌려주고 서비스를 부르지 않는다")
    void 숫자가_아닌_id는_400() throws Exception {
        // when & then
        mockMvc.perform(get("/api/loans/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        then(loanAccountDetailService).shouldHaveNoInteractions();
    }
}
