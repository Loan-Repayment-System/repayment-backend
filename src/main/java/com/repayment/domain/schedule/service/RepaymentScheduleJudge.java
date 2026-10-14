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
        if (FINAL_STATUSES.contains(target.getRepaymentScheduleStatusId())) {
            return RepaymentScheduleJudgment.KEEP;
        }

        LocalDate scheduledDate = target.getScheduledDate();
        if (target.getConfirmedAt() == null) {
            if (businessDate.isBefore(scheduledDate.minusDays(CONFIRM_DAYS_BEFORE_SCHEDULED_DATE))) {
                return RepaymentScheduleJudgment.KEEP;
            }
            // 고객이 낼 기회가 없었던 회차를 연체로 만들지 않는다
            return businessDate.isAfter(scheduledDate)
                    ? RepaymentScheduleJudgment.UNCONFIRMED_PAST_DUE
                    : RepaymentScheduleJudgment.CONFIRM;
        }

        if (remainingAmount(target).signum() == 0) {
            return RepaymentScheduleJudgment.PAID;
        }
        return businessDate.isAfter(scheduledDate)
                ? RepaymentScheduleJudgment.OVERDUE
                : RepaymentScheduleJudgment.DUE;
    }

    private BigDecimal remainingAmount(RepaymentScheduleJudgmentTargetResponse target) {
        return target.getScheduledPrincipal().add(target.getScheduledInterest())
                .subtract(target.getPaidPrincipal())
                .subtract(target.getPaidInterest());
    }
}
