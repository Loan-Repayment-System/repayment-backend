package com.repayment.domain.schedule.service;

import com.repayment.domain.baserate.exception.BaseRateNotFoundException;
import com.repayment.domain.schedule.dto.response.AppliedRateResponse;
import com.repayment.domain.schedule.dto.response.RepaymentAmountResponse;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleConfirmTargetResponse;
import com.repayment.domain.schedule.exception.ConfirmDateOutOfRangeException;
import com.repayment.domain.schedule.exception.InstallmentConfirmOrderException;
import com.repayment.domain.schedule.exception.RepaymentScheduleNotConfirmableException;
import com.repayment.domain.schedule.exception.RepaymentScheduleNotFoundException;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.global.code.InterestType;
import com.repayment.global.code.RepaymentMethod;
import com.repayment.global.code.RepaymentScheduleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("회차 상환금 확정")
class RepaymentScheduleConfirmServiceTest {

    private static final long REPAYMENT_SCHEDULE_ID = 100L;
    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final LocalDate SCHEDULED_DATE = LocalDate.of(2026, 11, 10);
    private static final LocalDate FIVE_DAYS_BEFORE = LocalDate.of(2026, 11, 5);

    @Mock
    private RepaymentScheduleMapper repaymentScheduleMapper;

    @Mock
    private AppliedRateService appliedRateService;

    @Spy
    private RepaymentAmountCalculator repaymentAmountCalculator = new RepaymentAmountCalculator();

    @InjectMocks
    private RepaymentScheduleConfirmService repaymentScheduleConfirmService;

    @Nested
    @DisplayName("원리금균등·고정금리 6%(기준 4.5% + 가산 1.5%), 원금 12,000,000원, 12회, 예정일 11/10인 예정 회차일 때")
    class 원리금균등_고정금리_예정_회차 {

        // 프로그램 설계 상환금 확정: 예정일 5일 전이 된 회차를 확정한다
        @Test
        @DisplayName("1회차를 기준일 11/05에 확정하면 원금 972,797원, 이자 60,000원을 납부 기한 도래로 저장하고 마지막 확정 회차를 1로 올린다")
        void 예정일_5일_전에_첫_회차를_확정한다() {
            // given
            givenTarget(target(1, 0, "0"));
            givenFixedRate();

            // when
            repaymentScheduleConfirmService.confirm(REPAYMENT_SCHEDULE_ID, FIVE_DAYS_BEFORE);

            // then
            Confirmation confirmation = captureConfirmation();
            assertThat(confirmation.amount().getPrincipal()).isEqualByComparingTo("972797");
            assertThat(confirmation.amount().getInterest()).isEqualByComparingTo("60000");
            assertThat(confirmation.rate().getBaseRate()).isEqualByComparingTo("4.5");
            assertThat(confirmation.rate().getAdditionalRate()).isEqualByComparingTo("1.5");
            assertThat(confirmation.status()).isEqualTo(RepaymentScheduleStatus.DUE);
            then(repaymentScheduleMapper).should().updateLastConfirmedInstallmentNo(LOAN_ACCOUNT_ID, 1);
        }

        // 프로그램 설계 상환금 확정: B = P0 − 앞 회차 원금 합, n = N − 회차 + 1
        @Test
        @DisplayName("2회차(앞 원금 합 972,797원)를 예정일 당일 11/10에 확정하면 원금 977,661원, 이자 55,136원을 저장하고 마지막 확정 회차를 2로 올린다")
        void 예정일_당일에_계산_잔액으로_확정한다() {
            // given
            givenTarget(target(2, 1, "972797"));
            givenFixedRate();

            // when
            repaymentScheduleConfirmService.confirm(REPAYMENT_SCHEDULE_ID, SCHEDULED_DATE);

            // then
            Confirmation confirmation = captureConfirmation();
            assertThat(confirmation.amount().getPrincipal()).isEqualByComparingTo("977661");
            assertThat(confirmation.amount().getInterest()).isEqualByComparingTo("55136");
            then(repaymentScheduleMapper).should().updateLastConfirmedInstallmentNo(LOAN_ACCOUNT_ID, 2);
        }

        @Test
        @DisplayName("기준일이 11/04(예정일 6일 전)면 ConfirmDateOutOfRangeException을 던지고 아무것도 저장하지 않는다")
        void 예정일_5일_전보다_이르면_확정하지_않는다() {
            // given
            givenTarget(target(1, 0, "0"));

            // when
            Throwable thrown = catchThrowable(() ->
                    repaymentScheduleConfirmService.confirm(REPAYMENT_SCHEDULE_ID, LocalDate.of(2026, 11, 4)));

            // then
            assertThat(thrown).isInstanceOf(ConfirmDateOutOfRangeException.class);
            thenNothingSaved();
        }

        // 프로그램 설계 2단계 판정 트리: 미확정인데 예정일이 지났으면 확정하지 않는다
        @Test
        @DisplayName("기준일이 11/11(예정일 다음 날)이면 ConfirmDateOutOfRangeException을 던지고 아무것도 저장하지 않는다")
        void 예정일이_지났으면_확정하지_않는다() {
            // given
            givenTarget(target(1, 0, "0"));

            // when
            Throwable thrown = catchThrowable(() ->
                    repaymentScheduleConfirmService.confirm(REPAYMENT_SCHEDULE_ID, LocalDate.of(2026, 11, 11)));

            // then
            assertThat(thrown).isInstanceOf(ConfirmDateOutOfRangeException.class);
            thenNothingSaved();
        }

        @Test
        @DisplayName("3회차인데 마지막 확정이 1회차면 InstallmentConfirmOrderException을 던지고 아무것도 저장하지 않는다")
        void 앞_회차가_미확정이면_확정하지_않는다() {
            // given
            givenTarget(target(3, 1, "972797"));

            // when
            Throwable thrown = catchThrowable(() ->
                    repaymentScheduleConfirmService.confirm(REPAYMENT_SCHEDULE_ID, FIVE_DAYS_BEFORE));

            // then
            assertThat(thrown).isInstanceOf(InstallmentConfirmOrderException.class);
            thenNothingSaved();
        }
    }

    @Nested
    @DisplayName("회차가 예정 상태가 아닐 때")
    class 예정_상태_아님 {

        @Test
        @DisplayName("이미 확정해 납부 기한 도래인 1회차면 RepaymentScheduleNotConfirmableException을 던지고 아무것도 저장하지 않는다")
        void 확정한_회차는_다시_확정하지_않는다() {
            // given
            givenTarget(target(RepaymentScheduleStatus.DUE, InterestType.FIXED, 1, 1, "0"));

            // when
            Throwable thrown = catchThrowable(() ->
                    repaymentScheduleConfirmService.confirm(REPAYMENT_SCHEDULE_ID, FIVE_DAYS_BEFORE));

            // then
            assertThat(thrown).isInstanceOf(RepaymentScheduleNotConfirmableException.class);
            thenNothingSaved();
        }

        @Test
        @DisplayName("마지막 확정이 3회차라 순서가 맞아도 기한이익상실로 종료된 4회차면 RepaymentScheduleNotConfirmableException을 던지고 아무것도 저장하지 않는다")
        void 닫힌_회차는_확정하지_않는다() {
            // given
            givenTarget(target(RepaymentScheduleStatus.CLOSED_BY_ACCELERATION, InterestType.FIXED, 4, 3, "2932007"));

            // when
            Throwable thrown = catchThrowable(() ->
                    repaymentScheduleConfirmService.confirm(REPAYMENT_SCHEDULE_ID, FIVE_DAYS_BEFORE));

            // then
            assertThat(thrown).isInstanceOf(RepaymentScheduleNotConfirmableException.class);
            thenNothingSaved();
        }
    }

    @Nested
    @DisplayName("원금만기일시상환·고정금리 0%(기준 0% + 가산 0%), 3회 대출일 때")
    class 만기일시_0퍼센트_대출 {

        @Test
        @DisplayName("1회차를 확정하면 원금 0원, 이자 0원을 납입 완료로 저장한다")
        void 영원_회차는_납입_완료로_저장한다() {
            // given
            givenTarget(RepaymentScheduleConfirmTargetResponse.builder()
                    .repaymentScheduleId(REPAYMENT_SCHEDULE_ID)
                    .loanAccountId(LOAN_ACCOUNT_ID)
                    .installmentNo(1)
                    .repaymentScheduleStatusId(RepaymentScheduleStatus.SCHEDULED)
                    .scheduledDate(SCHEDULED_DATE)
                    .interestTypeId(InterestType.FIXED)
                    .repaymentMethodId(RepaymentMethod.BULLET)
                    .contractBaseRate(BigDecimal.ZERO)
                    .contractAdditionalRate(BigDecimal.ZERO)
                    .contractPrincipal(new BigDecimal("10000000"))
                    .totalInstallments(3)
                    .lastConfirmedInstallmentNo(0)
                    .priorScheduledPrincipalSum(BigDecimal.ZERO)
                    .build());
            given(appliedRateService.resolve(InterestType.FIXED, BigDecimal.ZERO, BigDecimal.ZERO, SCHEDULED_DATE))
                    .willReturn(new AppliedRateResponse(BigDecimal.ZERO, BigDecimal.ZERO));

            // when
            repaymentScheduleConfirmService.confirm(REPAYMENT_SCHEDULE_ID, FIVE_DAYS_BEFORE);

            // then
            Confirmation confirmation = captureConfirmation();
            assertThat(confirmation.amount().getPrincipal()).isEqualByComparingTo("0");
            assertThat(confirmation.amount().getInterest()).isEqualByComparingTo("0");
            assertThat(confirmation.status()).isEqualTo(RepaymentScheduleStatus.PAID);
        }
    }

    @Nested
    @DisplayName("변동금리 대출에 적용할 기준금리가 없을 때")
    class 변동금리_기준금리_없음 {

        @Test
        @DisplayName("BaseRateNotFoundException을 그대로 던지고 아무것도 저장하지 않는다")
        void 금리를_모르면_확정하지_않는다() {
            // given
            givenTarget(target(RepaymentScheduleStatus.SCHEDULED, InterestType.VARIABLE, 1, 0, "0"));
            given(appliedRateService.resolve(any(), any(), any(), any()))
                    .willThrow(new BaseRateNotFoundException(FIVE_DAYS_BEFORE));

            // when
            Throwable thrown = catchThrowable(() ->
                    repaymentScheduleConfirmService.confirm(REPAYMENT_SCHEDULE_ID, FIVE_DAYS_BEFORE));

            // then
            assertThat(thrown).isInstanceOf(BaseRateNotFoundException.class);
            thenNothingSaved();
        }
    }

    @Nested
    @DisplayName("회차가 없을 때")
    class 회차_없음 {

        @Test
        @DisplayName("회차 ID를 담은 RepaymentScheduleNotFoundException을 던지고 아무것도 저장하지 않는다")
        void 없는_회차는_원인을_담아_멈춘다() {
            // given
            given(repaymentScheduleMapper.findConfirmTarget(REPAYMENT_SCHEDULE_ID)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() ->
                    repaymentScheduleConfirmService.confirm(REPAYMENT_SCHEDULE_ID, FIVE_DAYS_BEFORE));

            // then
            assertThat(thrown).isInstanceOf(RepaymentScheduleNotFoundException.class);
            assertThat(((RepaymentScheduleNotFoundException) thrown).getRepaymentScheduleId())
                    .isEqualTo(REPAYMENT_SCHEDULE_ID);
            thenNothingSaved();
        }
    }

    private void givenTarget(RepaymentScheduleConfirmTargetResponse target) {
        given(repaymentScheduleMapper.findConfirmTarget(REPAYMENT_SCHEDULE_ID)).willReturn(Optional.of(target));
    }

    private void givenFixedRate() {
        given(appliedRateService.resolve(
                InterestType.FIXED, new BigDecimal("4.5"), new BigDecimal("1.5"), SCHEDULED_DATE))
                .willReturn(new AppliedRateResponse(new BigDecimal("4.5"), new BigDecimal("1.5")));
    }

    private static RepaymentScheduleConfirmTargetResponse target(int installmentNo, int lastConfirmedInstallmentNo,
                                                                 String priorScheduledPrincipalSum) {
        return target(RepaymentScheduleStatus.SCHEDULED, InterestType.FIXED, installmentNo,
                lastConfirmedInstallmentNo, priorScheduledPrincipalSum);
    }

    // 원리금균등, 기준 4.5% + 가산 1.5%, 원금 12,000,000원, 12회, 예정일 11/10
    private static RepaymentScheduleConfirmTargetResponse target(RepaymentScheduleStatus status,
                                                                 InterestType interestType, int installmentNo,
                                                                 int lastConfirmedInstallmentNo,
                                                                 String priorScheduledPrincipalSum) {
        return RepaymentScheduleConfirmTargetResponse.builder()
                .repaymentScheduleId(REPAYMENT_SCHEDULE_ID)
                .loanAccountId(LOAN_ACCOUNT_ID)
                .installmentNo(installmentNo)
                .repaymentScheduleStatusId(status)
                .scheduledDate(SCHEDULED_DATE)
                .interestTypeId(interestType)
                .repaymentMethodId(RepaymentMethod.EQUAL_PRINCIPAL_INTEREST)
                .contractBaseRate(new BigDecimal("4.5"))
                .contractAdditionalRate(new BigDecimal("1.5"))
                .contractPrincipal(new BigDecimal("12000000"))
                .totalInstallments(12)
                .lastConfirmedInstallmentNo(lastConfirmedInstallmentNo)
                .priorScheduledPrincipalSum(new BigDecimal(priorScheduledPrincipalSum))
                .build();
    }

    private Confirmation captureConfirmation() {
        ArgumentCaptor<RepaymentAmountResponse> amount = ArgumentCaptor.forClass(RepaymentAmountResponse.class);
        ArgumentCaptor<AppliedRateResponse> rate = ArgumentCaptor.forClass(AppliedRateResponse.class);
        ArgumentCaptor<RepaymentScheduleStatus> status = ArgumentCaptor.forClass(RepaymentScheduleStatus.class);
        then(repaymentScheduleMapper).should()
                .updateConfirmation(eq(REPAYMENT_SCHEDULE_ID), amount.capture(), rate.capture(), status.capture());
        return new Confirmation(amount.getValue(), rate.getValue(), status.getValue());
    }

    private void thenNothingSaved() {
        then(repaymentScheduleMapper).should(never()).updateConfirmation(anyLong(), any(), any(), any());
        then(repaymentScheduleMapper).should(never()).updateLastConfirmedInstallmentNo(anyLong(), anyInt());
    }

    private record Confirmation(RepaymentAmountResponse amount, AppliedRateResponse rate,
                                RepaymentScheduleStatus status) {
    }
}
