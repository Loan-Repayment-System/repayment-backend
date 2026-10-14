package com.repayment.domain.schedule.service;

import com.repayment.domain.schedule.dto.response.RepaymentScheduleJudgmentTargetResponse;
import com.repayment.global.code.RepaymentScheduleStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * 회차 한 행의 값과 기준일만 보고 판정 결과를 정한다.
 */
@Component
public class RepaymentScheduleJudge {

    private static final int CONFIRM_DAYS_BEFORE_SCHEDULED_DATE = 5;
    private static final Set<RepaymentScheduleStatus> FINAL_STATUSES = EnumSet.of(
            RepaymentScheduleStatus.PAID,
            RepaymentScheduleStatus.CLOSED_BY_ACCELERATION,
            RepaymentScheduleStatus.CLOSED_BY_MATURITY_OVERDUE);

    public RepaymentScheduleJudgment judge(RepaymentScheduleJudgmentTargetResponse target, LocalDate businessDate) {
        if (isFinal(target)) {
            return RepaymentScheduleJudgment.KEEP;
        }
        if (isConfirmed(target)) {
            return judgeConfirmed(target, businessDate);
        }
        return judgeUnconfirmed(target, businessDate);
    }

    /**
     * 미확정 회차: 예정일 5일 전부터 예정일까지 확정한다.
     */
    private RepaymentScheduleJudgment judgeUnconfirmed(RepaymentScheduleJudgmentTargetResponse target,
                                                       LocalDate businessDate) {
        if (isBeforeConfirmDate(target, businessDate)) {
            return RepaymentScheduleJudgment.KEEP;
        }
        // 고객이 낼 기회가 없었던 회차를 연체로 만들지 않는다
        if (isPastScheduledDate(target, businessDate)) {
            return RepaymentScheduleJudgment.UNCONFIRMED_PAST_DUE;
        }
        return RepaymentScheduleJudgment.CONFIRM;
    }

    /**
     * 확정 회차: 다 냈으면 납입 완료, 남았는데 예정일이 지났으면 연체, 아니면 납부 기한 도래다.
     */
    private RepaymentScheduleJudgment judgeConfirmed(RepaymentScheduleJudgmentTargetResponse target,
                                                     LocalDate businessDate) {
        if (isFullyPaid(target)) {
            return RepaymentScheduleJudgment.PAID;
        }
        if (isPastScheduledDate(target, businessDate)) {
            return RepaymentScheduleJudgment.OVERDUE;
        }
        return RepaymentScheduleJudgment.DUE;
    }

    private boolean isFinal(RepaymentScheduleJudgmentTargetResponse target) {
        return FINAL_STATUSES.contains(target.getRepaymentScheduleStatusId());
    }

    private boolean isConfirmed(RepaymentScheduleJudgmentTargetResponse target) {
        return target.getConfirmedAt() != null;
    }

    private boolean isBeforeConfirmDate(RepaymentScheduleJudgmentTargetResponse target, LocalDate businessDate) {
        LocalDate confirmDate = target.getScheduledDate().minusDays(CONFIRM_DAYS_BEFORE_SCHEDULED_DATE);
        return businessDate.isBefore(confirmDate);
    }

    private boolean isPastScheduledDate(RepaymentScheduleJudgmentTargetResponse target, LocalDate businessDate) {
        return businessDate.isAfter(target.getScheduledDate());
    }

    private boolean isFullyPaid(RepaymentScheduleJudgmentTargetResponse target) {
        BigDecimal remaining = target.getScheduledPrincipal().add(target.getScheduledInterest())
                .subtract(target.getPaidPrincipal())
                .subtract(target.getPaidInterest());
        return remaining.signum() == 0;
    }
}
