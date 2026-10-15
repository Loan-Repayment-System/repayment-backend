package com.repayment.domain.loan.service;

import com.repayment.domain.acceleration.service.AccelerationConversionService;
import com.repayment.domain.acceleration.service.AccelerationDailyChargeService;
import com.repayment.domain.delinquency.service.InstallmentDelinquencyChargeService;
import com.repayment.domain.loan.dto.response.LoanStatusJudgmentResponse;
import com.repayment.domain.loan.exception.LoanStatusJudgmentNotFoundException;
import com.repayment.domain.loan.mapper.LoanStatusJudgmentMapper;
import com.repayment.domain.maturity.service.MaturityOverdueConversionService;
import com.repayment.domain.maturity.service.MaturityOverdueDailyChargeService;
import com.repayment.global.code.LoanJudgmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
@DisplayName("상태 변화에 따른 처리")
class LoanStatusChangeServiceTest {

    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2027, 1, 11);

    @Mock
    private LoanStatusJudgmentMapper loanStatusJudgmentMapper;

    @Mock
    private LoanStatusUpdateService loanStatusUpdateService;

    @Mock
    private InstallmentDelinquencyChargeService installmentDelinquencyChargeService;

    @Mock
    private AccelerationConversionService accelerationConversionService;

    @Mock
    private AccelerationDailyChargeService accelerationDailyChargeService;

    @Mock
    private MaturityOverdueConversionService maturityOverdueConversionService;

    @Mock
    private MaturityOverdueDailyChargeService maturityOverdueDailyChargeService;

    @InjectMocks
    private LoanStatusChangeService loanStatusChangeService;

    @Nested
    @DisplayName("오늘 판정이 정상·완제일 때")
    class 정상_완제 {

        @Test
        @DisplayName("직전 판정 행이 없고 오늘 0 정상이면 대출 상태만 갱신한다")
        void 정상이면_상태만_갱신한다() {
            // given
            givenJudgments(today(LoanJudgmentStatus.NORMAL));

            // when
            loanStatusChangeService.process(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            then(loanStatusUpdateService).should().update(LOAN_ACCOUNT_ID, LoanJudgmentStatus.NORMAL);
            thenNoChargeOrConversion();
        }

        @Test
        @DisplayName("직전 1 만기 경과, 오늘 2 완제면 대출 상태만 갱신한다")
        void 완제면_상태만_갱신한다() {
            // given
            givenJudgments(today(LoanJudgmentStatus.PAID_OFF), previous(LoanJudgmentStatus.MATURITY_OVERDUE));

            // when
            loanStatusChangeService.process(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            then(loanStatusUpdateService).should().update(LOAN_ACCOUNT_ID, LoanJudgmentStatus.PAID_OFF);
            thenNoChargeOrConversion();
        }
    }

    @Nested
    @DisplayName("오늘 판정이 연체일 때")
    class 연체 {

        @Test
        @DisplayName("직전 0, 오늘 21이면 대출 상태를 갱신한 뒤 21로 회차 연체금을 쌓는다")
        void 회차_연체금을_쌓는다() {
            // given
            givenJudgments(today(LoanJudgmentStatus.INSTALLMENT_OVERDUE), previous(LoanJudgmentStatus.NORMAL));

            // when
            loanStatusChangeService.process(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            InOrder order = inOrder(loanStatusUpdateService, installmentDelinquencyChargeService);
            order.verify(loanStatusUpdateService).update(LOAN_ACCOUNT_ID, LoanJudgmentStatus.INSTALLMENT_OVERDUE);
            order.verify(installmentDelinquencyChargeService)
                    .accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE, LoanJudgmentStatus.INSTALLMENT_OVERDUE);
            then(accelerationConversionService).shouldHaveNoInteractions();
            then(accelerationDailyChargeService).shouldHaveNoInteractions();
            then(maturityOverdueConversionService).shouldHaveNoInteractions();
            then(maturityOverdueDailyChargeService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("오늘 판정이 기한이익상실일 때")
    class 기한이익상실 {

        @Test
        @DisplayName("직전 21, 오늘 22면 처음이라 대출 상태 갱신 → 기한이익상실 전환 → 22로 기한이익상실 연체금 쌓기 순서로 한다")
        void 처음이면_전환한_뒤_연체금을_쌓는다() {
            // given
            givenJudgments(today(LoanJudgmentStatus.INSTALLMENT_ACCELERATION), previous(LoanJudgmentStatus.INSTALLMENT_OVERDUE));

            // when
            loanStatusChangeService.process(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            InOrder order = inOrder(loanStatusUpdateService, accelerationConversionService, accelerationDailyChargeService);
            order.verify(loanStatusUpdateService).update(LOAN_ACCOUNT_ID, LoanJudgmentStatus.INSTALLMENT_ACCELERATION);
            order.verify(accelerationConversionService).convert(LOAN_ACCOUNT_ID, BUSINESS_DATE);
            order.verify(accelerationDailyChargeService)
                    .accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE, LoanJudgmentStatus.INSTALLMENT_ACCELERATION);
            then(installmentDelinquencyChargeService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("직전 23, 오늘 22면 ❻항만 풀린 '이어짐'이라 전환하지 않고 22로 기한이익상실 연체금만 쌓는다")
        void 이어지면_전환하지_않는다() {
            // given
            givenJudgments(today(LoanJudgmentStatus.INSTALLMENT_ACCELERATION),
                    previous(LoanJudgmentStatus.INSTALLMENT_ACCELERATION_CLAUSE_6));

            // when
            loanStatusChangeService.process(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            InOrder order = inOrder(loanStatusUpdateService, accelerationDailyChargeService);
            order.verify(loanStatusUpdateService).update(LOAN_ACCOUNT_ID, LoanJudgmentStatus.INSTALLMENT_ACCELERATION);
            order.verify(accelerationDailyChargeService)
                    .accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE, LoanJudgmentStatus.INSTALLMENT_ACCELERATION);
            then(accelerationConversionService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("오늘 판정이 만기 경과일 때")
    class 만기_경과 {

        @Test
        @DisplayName("직전 21, 오늘 1이면 처음이라 대출 상태 갱신 → 만기 경과 전환 → 만기 경과 연체금 쌓기 순서로 한다")
        void 처음이면_전환한_뒤_연체금을_쌓는다() {
            // given
            givenJudgments(today(LoanJudgmentStatus.MATURITY_OVERDUE), previous(LoanJudgmentStatus.INSTALLMENT_OVERDUE));

            // when
            loanStatusChangeService.process(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            InOrder order = inOrder(loanStatusUpdateService, maturityOverdueConversionService, maturityOverdueDailyChargeService);
            order.verify(loanStatusUpdateService).update(LOAN_ACCOUNT_ID, LoanJudgmentStatus.MATURITY_OVERDUE);
            order.verify(maturityOverdueConversionService).convert(LOAN_ACCOUNT_ID, BUSINESS_DATE);
            order.verify(maturityOverdueDailyChargeService).accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE);
            then(installmentDelinquencyChargeService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("직전 1, 오늘 1이면 '이어짐'이라 전환하지 않고 만기 경과 연체금만 쌓는다")
        void 이어지면_전환하지_않는다() {
            // given
            givenJudgments(today(LoanJudgmentStatus.MATURITY_OVERDUE), previous(LoanJudgmentStatus.MATURITY_OVERDUE));

            // when
            loanStatusChangeService.process(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            InOrder order = inOrder(loanStatusUpdateService, maturityOverdueDailyChargeService);
            order.verify(loanStatusUpdateService).update(LOAN_ACCOUNT_ID, LoanJudgmentStatus.MATURITY_OVERDUE);
            order.verify(maturityOverdueDailyChargeService).accrue(LOAN_ACCOUNT_ID, BUSINESS_DATE);
            then(maturityOverdueConversionService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("기준일의 판정이 없을 때")
    class 판정_없음 {

        @Test
        @DisplayName("판정 행이 없으면 LoanStatusJudgmentNotFoundException을 던지고 아무 모듈도 부르지 않는다")
        void 판정_행이_없으면_예외를_던진다() {
            // given
            givenJudgments();

            // when
            Throwable thrown = catchThrowable(() -> loanStatusChangeService.process(LOAN_ACCOUNT_ID, BUSINESS_DATE));

            // then
            assertThat(thrown).isInstanceOf(LoanStatusJudgmentNotFoundException.class);
            then(loanStatusUpdateService).shouldHaveNoInteractions();
            thenNoChargeOrConversion();
        }

        @Test
        @DisplayName("가장 최근 판정이 어제(2027-01-10) 행이면 LoanStatusJudgmentNotFoundException을 던지고 아무 모듈도 부르지 않는다")
        void 가장_최근_판정이_어제면_예외를_던진다() {
            // given
            givenJudgments(LoanStatusJudgmentResponse.builder()
                    .businessDate(BUSINESS_DATE.minusDays(1))
                    .loanJudgmentStatusId(LoanJudgmentStatus.INSTALLMENT_OVERDUE)
                    .build());

            // when
            Throwable thrown = catchThrowable(() -> loanStatusChangeService.process(LOAN_ACCOUNT_ID, BUSINESS_DATE));

            // then
            assertThat(thrown).isInstanceOf(LoanStatusJudgmentNotFoundException.class);
            then(loanStatusUpdateService).shouldHaveNoInteractions();
            thenNoChargeOrConversion();
        }
    }

    private void givenJudgments(LoanStatusJudgmentResponse... judgments) {
        given(loanStatusJudgmentMapper.findRecentJudgments(LOAN_ACCOUNT_ID)).willReturn(List.of(judgments));
    }

    private LoanStatusJudgmentResponse today(LoanJudgmentStatus status) {
        return LoanStatusJudgmentResponse.builder().businessDate(BUSINESS_DATE).loanJudgmentStatusId(status).build();
    }

    private LoanStatusJudgmentResponse previous(LoanJudgmentStatus status) {
        return LoanStatusJudgmentResponse.builder()
                .businessDate(BUSINESS_DATE.minusDays(1))
                .loanJudgmentStatusId(status)
                .build();
    }

    private void thenNoChargeOrConversion() {
        then(installmentDelinquencyChargeService).shouldHaveNoInteractions();
        then(accelerationConversionService).shouldHaveNoInteractions();
        then(accelerationDailyChargeService).shouldHaveNoInteractions();
        then(maturityOverdueConversionService).shouldHaveNoInteractions();
        then(maturityOverdueDailyChargeService).shouldHaveNoInteractions();
    }
}
