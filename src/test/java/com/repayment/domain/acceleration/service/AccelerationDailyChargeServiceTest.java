package com.repayment.domain.acceleration.service;

import com.repayment.domain.acceleration.dto.response.AccelerationDailyChargeResponse;
import com.repayment.domain.acceleration.dto.response.AccelerationDailyChargeTargetResponse;
import com.repayment.domain.acceleration.exception.AccelerationDelinquencyNotApplicableException;
import com.repayment.domain.acceleration.exception.LoanAccelerationNotFoundException;
import com.repayment.domain.acceleration.mapper.AccelerationDailyChargeMapper;
import com.repayment.domain.delinquency.service.DelinquencyChargeCalculator;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("기한이익상실 연체금 쌓기")
class AccelerationDailyChargeServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final long LOAN_ACCELERATION_ID = 50L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 15);

    @Mock
    private AccelerationDailyChargeMapper accelerationDailyChargeMapper;

    @Spy
    private DelinquencyChargeCalculator delinquencyChargeCalculator;

    @InjectMocks
    private AccelerationDailyChargeService accelerationDailyChargeService;

    // 적용 금리 4.5 + 1.5 = 6% → 연체 이자율 9%
    @Nested
    @DisplayName("그날 아직 쌓지 않았을 때")
    class 그날_아직_쌓지_않음 {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = LoanJudgmentStatus.class, names = {"BULLET_ACCELERATION", "INSTALLMENT_ACCELERATION"})
        @DisplayName("12·22면 원금잔액 10,000,000원 전체에 연체 이자율 9%로 연체금 2,465원을 쌓고 약정이자는 0원이다")
        void 원금잔액_전체에_연체금이_붙는다(LoanJudgmentStatus todayJudgment) {
            // given
            givenTarget("10000000", "78000", "8000", "1502000", "2000");

            // when
            accelerationDailyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE, todayJudgment);

            // then
            AccelerationDailyChargeResponse charge = capturedCharge();
            assertThat(charge.getLoanAccelerationId()).isEqualTo(LOAN_ACCELERATION_ID);
            assertThat(charge.getChargeDate()).isEqualTo(BUSINESS_DATE);
            assertThat(charge.getLoanJudgmentStatusId()).isEqualTo(todayJudgment);
            assertThat(charge.getDelinquencyTargetAmount()).isEqualByComparingTo("10000000");
            assertThat(charge.getDelinquencyRate()).isEqualByComparingTo("9");
            assertThat(charge.getDelinquencyCharge()).isEqualByComparingTo("2465");
            assertThat(charge.getInterestAmount()).isEqualByComparingTo("0");
            assertThat(charge.getDelinquencyStatusId()).isEqualTo(DelinquencyStatus.IN_PROGRESS);
        }

        // ❻항
        @Test
        @DisplayName("23이면 남은 옮긴 이자 70,000원과 남은 밀린 원금 1,500,000원에 연체금 387원, 나머지 원금 8,500,000원에 약정이자 1,397원을 쌓는다")
        void 분할상환_6항은_밀린_금액에_연체금_나머지_원금에_약정이자() {
            // given
            givenTarget("10000000", "78000", "8000", "1502000", "2000");

            // when
            accelerationDailyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE,
                    LoanJudgmentStatus.INSTALLMENT_ACCELERATION_CLAUSE_6);

            // then
            AccelerationDailyChargeResponse charge = capturedCharge();
            assertThat(charge.getDelinquencyTargetAmount()).isEqualByComparingTo("1570000");
            assertThat(charge.getDelinquencyCharge()).isEqualByComparingTo("387");
            assertThat(charge.getInterestAmount()).isEqualByComparingTo("1397");
            assertThat(charge.getDelinquencyStatusId()).isEqualTo(DelinquencyStatus.IN_PROGRESS);
        }

        // ❻항, 만기일시는 옮긴 밀린 원금이 0
        @Test
        @DisplayName("13이면 남은 옮긴 이자 70,000원에 연체금 17원, 원금잔액 10,000,000원에 약정이자 1,643원을 쌓는다")
        void 만기일시_6항은_옮긴_이자에_연체금_원금잔액에_약정이자() {
            // given
            givenTarget("10000000", "78000", "8000", "0", "0");

            // when
            accelerationDailyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE,
                    LoanJudgmentStatus.BULLET_ACCELERATION_CLAUSE_6);

            // then
            AccelerationDailyChargeResponse charge = capturedCharge();
            assertThat(charge.getDelinquencyTargetAmount()).isEqualByComparingTo("70000");
            assertThat(charge.getDelinquencyCharge()).isEqualByComparingTo("17");
            assertThat(charge.getInterestAmount()).isEqualByComparingTo("1643");
        }

        @Test
        @DisplayName("13에 옮긴 이자를 다 내고 원금잔액이 1,000원이면 연체금·약정이자 모두 0원을 '해소'로 쌓는다")
        void 연체금과_약정이자가_모두_0원이면_해소로_쌓는다() {
            // given
            givenTarget("1000", "78000", "78000", "0", "0");

            // when
            accelerationDailyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE,
                    LoanJudgmentStatus.BULLET_ACCELERATION_CLAUSE_6);

            // then
            AccelerationDailyChargeResponse charge = capturedCharge();
            assertThat(charge.getDelinquencyCharge()).isEqualByComparingTo("0");
            assertThat(charge.getInterestAmount()).isEqualByComparingTo("0");
            assertThat(charge.getDelinquencyStatusId()).isEqualTo(DelinquencyStatus.RESOLVED);
        }
    }

    @Nested
    @DisplayName("그날 이미 쌓았을 때")
    class 그날_이미_쌓음 {

        @Test
        @DisplayName("기준일 행이 있으면 아무것도 쌓지 않는다")
        void 아무것도_쌓지_않는다() {
            // given
            given(accelerationDailyChargeMapper.findAccrualTarget(LOAN_ACCOUNT_ID))
                    .willReturn(Optional.of(target("10000000", "78000", "8000", "1502000", "2000")));
            given(accelerationDailyChargeMapper.existsByLoanAccelerationIdAndChargeDate(LOAN_ACCELERATION_ID, BUSINESS_DATE))
                    .willReturn(true);

            // when
            accelerationDailyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE, LoanJudgmentStatus.INSTALLMENT_ACCELERATION);

            // then
            then(accelerationDailyChargeMapper).should(never()).insertCharge(any());
        }
    }

    @Nested
    @DisplayName("부르면 안 되는 상황일 때")
    class 잘못_부름 {

        @Test
        @DisplayName("전환 기록이 없으면 LoanAccelerationNotFoundException을 던지고 그날 행 확인도 기록도 하지 않는다")
        void 전환_기록이_없으면_예외를_던진다() {
            // given
            given(accelerationDailyChargeMapper.findAccrualTarget(LOAN_ACCOUNT_ID)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> accelerationDailyChargeService.accrue(
                    LOAN_ACCOUNT_ID, BUSINESS_DATE, LoanJudgmentStatus.INSTALLMENT_ACCELERATION));

            // then
            assertThat(thrown).isInstanceOf(LoanAccelerationNotFoundException.class);
            then(accelerationDailyChargeMapper).should(never()).existsByLoanAccelerationIdAndChargeDate(anyLong(), any());
            then(accelerationDailyChargeMapper).should(never()).insertCharge(any());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = LoanJudgmentStatus.class, names = {
                "NORMAL", "MATURITY_OVERDUE", "PAID_OFF", "BULLET_INTEREST_OVERDUE", "INSTALLMENT_OVERDUE"})
        @DisplayName("오늘 판정이 0·1·2·11·21이면 AccelerationDelinquencyNotApplicableException을 던지고 조회도 기록도 하지 않는다")
        void 기한이익상실이_아니면_예외를_던진다(LoanJudgmentStatus todayJudgment) {
            // given: todayJudgment

            // when
            Throwable thrown = catchThrowable(
                    () -> accelerationDailyChargeService.accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE, todayJudgment));

            // then
            assertThat(thrown).isInstanceOf(AccelerationDelinquencyNotApplicableException.class);
            then(accelerationDailyChargeMapper).shouldHaveNoInteractions();
        }
    }

    private void givenTarget(String principalBalance, String carriedInterest, String paidCarriedInterest,
                             String carriedOverduePrincipal, String paidCarriedOverduePrincipal) {
        given(accelerationDailyChargeMapper.findAccrualTarget(LOAN_ACCOUNT_ID)).willReturn(Optional.of(
                target(principalBalance, carriedInterest, paidCarriedInterest,
                        carriedOverduePrincipal, paidCarriedOverduePrincipal)));
        given(accelerationDailyChargeMapper.existsByLoanAccelerationIdAndChargeDate(LOAN_ACCELERATION_ID, BUSINESS_DATE))
                .willReturn(false);
    }

    private AccelerationDailyChargeTargetResponse target(String principalBalance, String carriedInterest,
                                                         String paidCarriedInterest, String carriedOverduePrincipal,
                                                         String paidCarriedOverduePrincipal) {
        return AccelerationDailyChargeTargetResponse.builder()
                .loanAccelerationId(LOAN_ACCELERATION_ID)
                .appliedBaseRate(new BigDecimal("4.5"))
                .appliedAdditionalRate(new BigDecimal("1.5"))
                .carriedInterest(new BigDecimal(carriedInterest))
                .paidCarriedInterest(new BigDecimal(paidCarriedInterest))
                .carriedOverduePrincipal(new BigDecimal(carriedOverduePrincipal))
                .paidCarriedOverduePrincipal(new BigDecimal(paidCarriedOverduePrincipal))
                .loanPrincipalBalance(new BigDecimal(principalBalance))
                .build();
    }

    private AccelerationDailyChargeResponse capturedCharge() {
        ArgumentCaptor<AccelerationDailyChargeResponse> captor =
                ArgumentCaptor.forClass(AccelerationDailyChargeResponse.class);
        then(accelerationDailyChargeMapper).should().insertCharge(captor.capture());
        return captor.getValue();
    }
}
