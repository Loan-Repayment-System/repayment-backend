package com.repayment.domain.schedule.service;

import com.repayment.domain.schedule.dto.response.RepaymentScheduleJudgmentTargetResponse;
import com.repayment.domain.schedule.exception.UnconfirmedInstallmentPastDueException;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.global.code.RepaymentScheduleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("회차 하나의 대출 스케줄 판정")
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
        @DisplayName("11/05에 판정하면 상환금 확정을 부르고 상태는 따로 바꾸지 않는다")
        void 확정할_때가_되면_상환금_확정을_부른다() {
            // given
            RepaymentScheduleJudgmentTargetResponse target = unconfirmed();
            LocalDate businessDate = LocalDate.of(2026, 11, 5);

            // when
            repaymentScheduleJudgmentService.judge(target, businessDate);

            // then
            then(repaymentScheduleConfirmService).should().confirm(REPAYMENT_SCHEDULE_ID, businessDate);
            then(repaymentScheduleMapper).shouldHaveNoInteractions();
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

    private RepaymentScheduleJudgmentTargetResponse unconfirmed() {
        return RepaymentScheduleJudgmentTargetResponse.builder()
                .repaymentScheduleId(REPAYMENT_SCHEDULE_ID)
                .loanAccountId(LOAN_ACCOUNT_ID)
                .repaymentScheduleStatusId(RepaymentScheduleStatus.SCHEDULED)
                .scheduledDate(SCHEDULED_DATE)
                .paidPrincipal(BigDecimal.ZERO)
                .paidInterest(BigDecimal.ZERO)
                .build();
    }

    private RepaymentScheduleJudgmentTargetResponse confirmed(RepaymentScheduleStatus status) {
        return RepaymentScheduleJudgmentTargetResponse.builder()
                .repaymentScheduleId(REPAYMENT_SCHEDULE_ID)
                .loanAccountId(LOAN_ACCOUNT_ID)
                .repaymentScheduleStatusId(status)
                .scheduledDate(SCHEDULED_DATE)
                .confirmedAt(LocalDateTime.of(2026, 11, 5, 0, 0))
                .scheduledPrincipal(new BigDecimal("1000000"))
                .scheduledInterest(new BigDecimal("50000"))
                .paidPrincipal(BigDecimal.ZERO)
                .paidInterest(new BigDecimal("50000"))
                .build();
    }
}
