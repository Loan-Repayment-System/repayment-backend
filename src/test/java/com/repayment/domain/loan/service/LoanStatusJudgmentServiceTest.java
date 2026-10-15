package com.repayment.domain.loan.service;

import com.repayment.domain.loan.dto.response.LoanStatusJudgmentTargetResponse;
import com.repayment.domain.loan.exception.LoanAccountNotFoundException;
import com.repayment.domain.loan.mapper.LoanStatusJudgmentMapper;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.RepaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("대출 상태 판정 기록")
class LoanStatusJudgmentServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 14);

    @Mock
    private LoanStatusJudgmentMapper loanStatusJudgmentMapper;

    @Mock
    private LoanPayoffService loanPayoffService;

    @Mock
    private LoanStatusJudge loanStatusJudge;

    @InjectMocks
    private LoanStatusJudgmentService loanStatusJudgmentService;

    @Nested
    @DisplayName("대출이 있을 때")
    class 대출이_있음 {

        @Test
        @DisplayName("조회한 값·완제 여부·기준일로 판정하고, 결과 21을 (대출, 기준일, 21) 1행으로 남긴다")
        void 판정하고_1행을_남긴다() {
            // given
            LoanStatusJudgmentTargetResponse target = LoanStatusJudgmentTargetResponse.builder()
                    .loanAccountId(LOAN_ACCOUNT_ID)
                    .previousJudgmentStatus(LoanJudgmentStatus.NORMAL)
                    .repaymentMethodId(RepaymentMethod.EQUAL_PRINCIPAL_INTEREST)
                    .contractPrincipal(new BigDecimal("30000000"))
                    .maturityDate(LocalDate.of(2027, 6, 30))
                    .overdueInstallmentCount(1)
                    .oldestOverdueDate(LocalDate.of(2026, 10, 10))
                    .build();
            given(loanStatusJudgmentMapper.findJudgmentTarget(LOAN_ACCOUNT_ID)).willReturn(Optional.of(target));
            given(loanPayoffService.isPaidOff(LOAN_ACCOUNT_ID)).willReturn(false);
            given(loanStatusJudge.judge(target, false, BUSINESS_DATE))
                    .willReturn(LoanJudgmentStatus.INSTALLMENT_OVERDUE);

            // when
            loanStatusJudgmentService.judge(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            then(loanStatusJudge).should().judge(target, false, BUSINESS_DATE);
            then(loanStatusJudgmentMapper).should()
                    .insertJudgment(LOAN_ACCOUNT_ID, BUSINESS_DATE, LoanJudgmentStatus.INSTALLMENT_OVERDUE);
        }
    }

    @Nested
    @DisplayName("대출이 없을 때")
    class 대출이_없음 {

        @Test
        @DisplayName("LoanAccountNotFoundException을 던지고 완제 확인도 기록도 하지 않는다")
        void 없는_대출은_아무것도_남기지_않는다() {
            // given
            given(loanStatusJudgmentMapper.findJudgmentTarget(LOAN_ACCOUNT_ID)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> loanStatusJudgmentService.judge(LOAN_ACCOUNT_ID, BUSINESS_DATE));

            // then
            assertThat(thrown).isInstanceOf(LoanAccountNotFoundException.class);
            then(loanPayoffService).shouldHaveNoInteractions();
            then(loanStatusJudgmentMapper).should(never()).insertJudgment(any(), any(), any());
        }
    }
}
