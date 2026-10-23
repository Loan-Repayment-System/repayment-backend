package com.repayment.domain.schedule.service;

import com.repayment.domain.schedule.dto.response.RepaymentAmountResponse;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleJudgmentTargetResponse;
import com.repayment.domain.schedule.exception.UnconfirmedInstallmentPastDueException;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.global.code.RepaymentScheduleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
@DisplayName("대출 스케줄 판정")
class RepaymentScheduleJudgmentServiceTest {

    private static final long REPAYMENT_SCHEDULE_ID = 100L;
    private static final long LOAN_ACCOUNT_ID = 10L;
    private static final LocalDate SCHEDULED_DATE = LocalDate.of(2026, 11, 10);

    @Spy
    private RepaymentScheduleJudge repaymentScheduleJudge = new RepaymentScheduleJudge();

    @Mock
    private RepaymentScheduleConfirmService repaymentScheduleConfirmService;

    @Mock
    private RepaymentScheduleMapper repaymentScheduleMapper;

    @InjectMocks
    private RepaymentScheduleJudgmentService repaymentScheduleJudgmentService;

    @Nested
    @DisplayName("금액이 확정되지 않은 회차일 때 (예정일 11/10)")
    class 미확정 {

        @Test
        @DisplayName("11/05에 판정해 원금 1,000,000원, 이자 50,000원으로 확정되면 납부 기한 도래로 바꾼다")
        void 확정하면_납부_기한_도래로_바꾼다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target = unconfirmed();
            LocalDate businessDate = LocalDate.of(2026, 11, 5);
            given(repaymentScheduleConfirmService.confirm(REPAYMENT_SCHEDULE_ID, businessDate))
                    .willReturn(new RepaymentAmountResponse(new BigDecimal("1000000"), new BigDecimal("50000")));

            // when
            repaymentScheduleJudgmentService.judge(target, businessDate);

            // then
            then(repaymentScheduleConfirmService).should().confirm(REPAYMENT_SCHEDULE_ID, businessDate);
            then(repaymentScheduleMapper).should().updateStatus(REPAYMENT_SCHEDULE_ID, RepaymentScheduleStatus.DUE);
        }

        @Test
        @DisplayName("11/05에 판정해 0원으로 확정되면 그날 바로 납입 완료로 바꾼다")
        void 영원으로_확정하면_납입_완료로_바꾼다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target = unconfirmed();
            LocalDate businessDate = LocalDate.of(2026, 11, 5);
            given(repaymentScheduleConfirmService.confirm(REPAYMENT_SCHEDULE_ID, businessDate))
                    .willReturn(new RepaymentAmountResponse(BigDecimal.ZERO, BigDecimal.ZERO));

            // when
            repaymentScheduleJudgmentService.judge(target, businessDate);

            // then
            then(repaymentScheduleConfirmService).should().confirm(REPAYMENT_SCHEDULE_ID, businessDate);
            then(repaymentScheduleMapper).should().updateStatus(REPAYMENT_SCHEDULE_ID, RepaymentScheduleStatus.PAID);
        }

        @Test
        @DisplayName("11/11에 판정하면 대출·회차 ID, 예정일, 기준일을 담아 UnconfirmedInstallmentPastDueException을 던지고 아무것도 바꾸지 않는다")
        void 예정일이_지났으면_예외를_던진다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target = unconfirmed();
            LocalDate businessDate = LocalDate.of(2026, 11, 11);

            // when
            Throwable thrown = catchThrowable(() -> repaymentScheduleJudgmentService.judge(target, businessDate));

            // then
            assertThat(thrown).isInstanceOfSatisfying(UnconfirmedInstallmentPastDueException.class, e -> {
                assertThat(e.getLoanAccountId()).isEqualTo(LOAN_ACCOUNT_ID);
                assertThat(e.getRepaymentScheduleId()).isEqualTo(REPAYMENT_SCHEDULE_ID);
                assertThat(e.getScheduledDate()).isEqualTo(SCHEDULED_DATE);
                assertThat(e.getBusinessDate()).isEqualTo(businessDate);
            });
            then(repaymentScheduleConfirmService).shouldHaveNoInteractions();
            then(repaymentScheduleMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("11/01에 판정하면 확정도 상태 변경도 하지 않는다")
        void 그대로면_손대지_않는다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target = unconfirmed();

            // when
            repaymentScheduleJudgmentService.judge(target, LocalDate.of(2026, 11, 1));

            // then
            then(repaymentScheduleConfirmService).shouldHaveNoInteractions();
            then(repaymentScheduleMapper).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("금액이 확정되고 이자만 낸 회차일 때 (예정일 11/10)")
    class 확정 {

        @Test
        @DisplayName("납부 기한 도래 회차를 11/11에 판정하면 상태를 연체로 바꾼다")
        void 상태가_바뀌면_저장한다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target = confirmed(RepaymentScheduleStatus.DUE);

            // when
            repaymentScheduleJudgmentService.judge(target, LocalDate.of(2026, 11, 11));

            // then
            then(repaymentScheduleMapper).should().updateStatus(REPAYMENT_SCHEDULE_ID, RepaymentScheduleStatus.OVERDUE);
            then(repaymentScheduleConfirmService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("이미 연체인 회차를 11/12에 판정하면 상태를 다시 쓰지 않는다")
        void 같은_상태면_저장하지_않는다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target = confirmed(RepaymentScheduleStatus.OVERDUE);

            // when
            repaymentScheduleJudgmentService.judge(target, LocalDate.of(2026, 11, 12));

            // then
            then(repaymentScheduleMapper).shouldHaveNoInteractions();
            then(repaymentScheduleConfirmService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("대출 하나의 회차를 모두 판정할 때 (기준일 12/05)")
    class 대출_단위 {

        private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 12, 5);
        private static final long THIRD_SCHEDULE_ID = 103L;
        private static final long FOURTH_SCHEDULE_ID = 104L;

        @Test
        @DisplayName("3회 납부 기한 도래(예정일 11/10)·4회 예정(예정일 12/10)이면 3회를 연체로 바꾼 뒤 4회를 확정해 납부 기한 도래로 바꾼다")
        void 조회한_순서대로_회차마다_판정한다() {
            // given
            given(repaymentScheduleMapper.findUnfinishedJudgmentTargets(LOAN_ACCOUNT_ID)).willReturn(List.of(
                    confirmed(THIRD_SCHEDULE_ID, RepaymentScheduleStatus.DUE, LocalDate.of(2026, 11, 10)),
                    unconfirmed(FOURTH_SCHEDULE_ID, LocalDate.of(2026, 12, 10))));
            given(repaymentScheduleConfirmService.confirm(FOURTH_SCHEDULE_ID, BUSINESS_DATE))
                    .willReturn(new RepaymentAmountResponse(new BigDecimal("1000000"), new BigDecimal("50000")));

            // when
            repaymentScheduleJudgmentService.judgeLoan(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            InOrder inOrder = inOrder(repaymentScheduleMapper, repaymentScheduleConfirmService);
            then(repaymentScheduleMapper).should(inOrder)
                    .updateStatus(THIRD_SCHEDULE_ID, RepaymentScheduleStatus.OVERDUE);
            then(repaymentScheduleConfirmService).should(inOrder).confirm(FOURTH_SCHEDULE_ID, BUSINESS_DATE);
            then(repaymentScheduleMapper).should(inOrder)
                    .updateStatus(FOURTH_SCHEDULE_ID, RepaymentScheduleStatus.DUE);
        }

        @Test
        @DisplayName("끝나지 않은 회차가 없으면 확정도 상태 변경도 하지 않는다")
        void 회차가_없으면_아무것도_하지_않는다() {
            // given
            given(repaymentScheduleMapper.findUnfinishedJudgmentTargets(LOAN_ACCOUNT_ID)).willReturn(List.of());

            // when
            repaymentScheduleJudgmentService.judgeLoan(LOAN_ACCOUNT_ID, BUSINESS_DATE);

            // then
            then(repaymentScheduleMapper).should().findUnfinishedJudgmentTargets(LOAN_ACCOUNT_ID);
            then(repaymentScheduleMapper).shouldHaveNoMoreInteractions();
            then(repaymentScheduleConfirmService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("3회가 미확정인데 예정일 11/10이 지났으면 UnconfirmedInstallmentPastDueException을 던지고 4회는 판정하지 않는다")
        void 실패하면_그_자리에서_멈춘다() {
            // given
            given(repaymentScheduleMapper.findUnfinishedJudgmentTargets(LOAN_ACCOUNT_ID)).willReturn(List.of(
                    unconfirmed(THIRD_SCHEDULE_ID, LocalDate.of(2026, 11, 10)),
                    unconfirmed(FOURTH_SCHEDULE_ID, LocalDate.of(2026, 12, 10))));

            // when
            Throwable thrown = catchThrowable(
                    () -> repaymentScheduleJudgmentService.judgeLoan(LOAN_ACCOUNT_ID, BUSINESS_DATE));

            // then
            assertThat(thrown).isInstanceOfSatisfying(UnconfirmedInstallmentPastDueException.class,
                    e -> assertThat(e.getRepaymentScheduleId()).isEqualTo(THIRD_SCHEDULE_ID));
            then(repaymentScheduleConfirmService).shouldHaveNoInteractions();
            then(repaymentScheduleMapper).should().findUnfinishedJudgmentTargets(LOAN_ACCOUNT_ID);
            then(repaymentScheduleMapper).shouldHaveNoMoreInteractions();
        }
    }

    private RepaymentScheduleJudgmentTargetResponse unconfirmed() {
        return unconfirmed(REPAYMENT_SCHEDULE_ID, SCHEDULED_DATE);
    }

    private RepaymentScheduleJudgmentTargetResponse unconfirmed(long repaymentScheduleId, LocalDate scheduledDate) {
        return RepaymentScheduleJudgmentTargetResponse.builder()
                .repaymentScheduleId(repaymentScheduleId)
                .loanAccountId(LOAN_ACCOUNT_ID)
                .repaymentScheduleStatusId(RepaymentScheduleStatus.SCHEDULED)
                .scheduledDate(scheduledDate)
                .paidPrincipal(BigDecimal.ZERO)
                .paidInterest(BigDecimal.ZERO)
                .build();
    }

    private RepaymentScheduleJudgmentTargetResponse confirmed(RepaymentScheduleStatus status) {
        return confirmed(REPAYMENT_SCHEDULE_ID, status, SCHEDULED_DATE);
    }

    private RepaymentScheduleJudgmentTargetResponse confirmed(long repaymentScheduleId, RepaymentScheduleStatus status,
                                                              LocalDate scheduledDate) {
        return RepaymentScheduleJudgmentTargetResponse.builder()
                .repaymentScheduleId(repaymentScheduleId)
                .loanAccountId(LOAN_ACCOUNT_ID)
                .repaymentScheduleStatusId(status)
                .scheduledDate(scheduledDate)
                .confirmedAt(scheduledDate.minusDays(5).atStartOfDay())
                .scheduledPrincipal(new BigDecimal("1000000"))
                .scheduledInterest(new BigDecimal("50000"))
                .paidPrincipal(BigDecimal.ZERO)
                .paidInterest(new BigDecimal("50000"))
                .build();
    }
}
