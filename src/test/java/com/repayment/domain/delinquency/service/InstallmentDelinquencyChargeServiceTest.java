package com.repayment.domain.delinquency.service;

import com.repayment.domain.delinquency.dto.response.InstallmentDelinquencyChargeResponse;
import com.repayment.domain.delinquency.dto.response.InstallmentDelinquencyChargeTargetResponse;
import com.repayment.domain.delinquency.exception.InstallmentDelinquencyNotApplicableException;
import com.repayment.domain.delinquency.mapper.InstallmentDelinquencyChargeMapper;
import com.repayment.global.code.DelinquencyStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("회차 연체금 쌓기")
class InstallmentDelinquencyChargeServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final long REPAYMENT_SCHEDULE_ID = 100L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 15);

    @Mock
    private InstallmentDelinquencyChargeMapper installmentDelinquencyChargeMapper;

    @Spy
    private DelinquencyChargeCalculator delinquencyChargeCalculator;

    @InjectMocks
    private InstallmentDelinquencyChargeService installmentDelinquencyChargeService;

    @Nested
    @DisplayName("오늘 판정이 11·21이고 '연체' 회차가 있을 때")
    class 연체_회차가_있음 {

        // 원금 800,000 / 이자 50,000 / 낸 이자 20,000 / 적용 금리 4.5 + 1.5 = 6% → 연체 이자율 9%
        @Test
        @DisplayName("21 1회 연체면 남은 원금+이자 830,000원에 연체 이자율 9%로 하루 연체금 204원을 '진행중'으로 쌓는다")
        void 분할상환_1회_연체는_남은_원금과_이자에_붙는다() {
            // given
            given(installmentDelinquencyChargeMapper.findAccrualTargets(LOAN_ACCOUNT_ID, BUSINESS_DATE))
                    .willReturn(List.of(target("50000", "20000")));

            // when
            installmentDelinquencyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE, LoanJudgmentStatus.INSTALLMENT_OVERDUE);

            // then
            InstallmentDelinquencyChargeResponse charge = capturedCharge();
            assertThat(charge.getRepaymentScheduleId()).isEqualTo(REPAYMENT_SCHEDULE_ID);
            assertThat(charge.getChargeDate()).isEqualTo(BUSINESS_DATE);
            assertThat(charge.getLoanJudgmentStatusId()).isEqualTo(LoanJudgmentStatus.INSTALLMENT_OVERDUE);
            assertThat(charge.getDelinquencyTargetAmount()).isEqualByComparingTo("830000");
            assertThat(charge.getDelinquencyRate()).isEqualByComparingTo("9");
            assertThat(charge.getDelinquencyCharge()).isEqualByComparingTo("204");
            assertThat(charge.getDelinquencyStatusId()).isEqualTo(DelinquencyStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("11 이자연체면 남은 이자 30,000원에만 붙어 하루 연체금은 7원이다")
        void 만기일시_이자연체는_남은_이자에만_붙는다() {
            // given
            given(installmentDelinquencyChargeMapper.findAccrualTargets(LOAN_ACCOUNT_ID, BUSINESS_DATE))
                    .willReturn(List.of(target("50000", "20000")));

            // when
            installmentDelinquencyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE, LoanJudgmentStatus.BULLET_INTEREST_OVERDUE);

            // then
            InstallmentDelinquencyChargeResponse charge = capturedCharge();
            assertThat(charge.getLoanJudgmentStatusId()).isEqualTo(LoanJudgmentStatus.BULLET_INTEREST_OVERDUE);
            assertThat(charge.getDelinquencyTargetAmount()).isEqualByComparingTo("30000");
            assertThat(charge.getDelinquencyCharge()).isEqualByComparingTo("7");
        }

        @Test
        @DisplayName("11 이자연체에 남은 이자가 1,000원이면 하루 연체금 0원을 '해소'로 쌓는다")
        void 연체금이_0원이면_해소로_쌓는다() {
            // given
            given(installmentDelinquencyChargeMapper.findAccrualTargets(LOAN_ACCOUNT_ID, BUSINESS_DATE))
                    .willReturn(List.of(target("50000", "49000")));

            // when
            installmentDelinquencyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE, LoanJudgmentStatus.BULLET_INTEREST_OVERDUE);

            // then
            InstallmentDelinquencyChargeResponse charge = capturedCharge();
            assertThat(charge.getDelinquencyCharge()).isEqualByComparingTo("0");
            assertThat(charge.getDelinquencyStatusId()).isEqualTo(DelinquencyStatus.RESOLVED);
        }
    }

    @Nested
    @DisplayName("쌓을 회차가 없을 때")
    class 쌓을_회차가_없음 {

        @Test
        @DisplayName("기준일 연체금을 이미 쌓아 조회된 회차가 없으면 아무것도 쌓지 않는다")
        void 아무것도_쌓지_않는다() {
            // given
            given(installmentDelinquencyChargeMapper.findAccrualTargets(LOAN_ACCOUNT_ID, BUSINESS_DATE))
                    .willReturn(List.of());

            // when
            installmentDelinquencyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE, LoanJudgmentStatus.INSTALLMENT_OVERDUE);

            // then
            then(installmentDelinquencyChargeMapper).should(never()).insertCharge(any());
        }
    }

    @Nested
    @DisplayName("오늘 판정이 11·21이 아닐 때")
    class 쌓을_판정이_아님 {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = LoanJudgmentStatus.class, mode = EnumSource.Mode.EXCLUDE,
                names = {"BULLET_INTEREST_OVERDUE", "INSTALLMENT_OVERDUE"})
        @DisplayName("0·1·2·12·13·22·23이면 InstallmentDelinquencyNotApplicableException을 던지고 회차를 조회하지도 쌓지도 않는다")
        void 예외를_던진다(LoanJudgmentStatus todayJudgment) {
            // given: todayJudgment

            // when
            Throwable thrown = catchThrowable(
                    () -> installmentDelinquencyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE, todayJudgment));

            // then
            assertThat(thrown).isInstanceOf(InstallmentDelinquencyNotApplicableException.class);
            then(installmentDelinquencyChargeMapper).shouldHaveNoInteractions();
        }
    }

    private InstallmentDelinquencyChargeTargetResponse target(String scheduledInterest, String paidInterest) {
        return InstallmentDelinquencyChargeTargetResponse.builder()
                .repaymentScheduleId(REPAYMENT_SCHEDULE_ID)
                .scheduledPrincipal(new BigDecimal("800000"))
                .scheduledInterest(new BigDecimal(scheduledInterest))
                .paidPrincipal(BigDecimal.ZERO)
                .paidInterest(new BigDecimal(paidInterest))
                .appliedBaseRate(new BigDecimal("4.5"))
                .appliedAdditionalRate(new BigDecimal("1.5"))
                .build();
    }

    private InstallmentDelinquencyChargeResponse capturedCharge() {
        ArgumentCaptor<InstallmentDelinquencyChargeResponse> captor =
                ArgumentCaptor.forClass(InstallmentDelinquencyChargeResponse.class);
        then(installmentDelinquencyChargeMapper).should().insertCharge(captor.capture());
        return captor.getValue();
    }
}
