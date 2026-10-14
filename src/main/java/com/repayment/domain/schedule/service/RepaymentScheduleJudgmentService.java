package com.repayment.domain.schedule.service;

import com.repayment.domain.schedule.dto.response.RepaymentAmountResponse;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleJudgmentTargetResponse;
import com.repayment.domain.schedule.exception.UnconfirmedInstallmentPastDueException;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.global.code.RepaymentScheduleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 회차 하나를 기준일에 맞게 판정하고, 결과대로 확정하거나 상태를 바꾼다.
 * 어떤 회차를 어떤 순서로 넘길지는 부르는 쪽이 정한다.
 */
@Service
@RequiredArgsConstructor
public class RepaymentScheduleJudgmentService {

    private final RepaymentScheduleJudge repaymentScheduleJudge;
    private final RepaymentScheduleConfirmService repaymentScheduleConfirmService;
    private final RepaymentScheduleMapper repaymentScheduleMapper;

    @Transactional
    public void judge(RepaymentScheduleJudgmentTargetResponse target, LocalDate businessDate) {
        RepaymentScheduleJudgment judgment = repaymentScheduleJudge.judge(target, businessDate);
        switch (judgment) {
            case KEEP -> {
            }
            case CONFIRM -> {
                RepaymentAmountResponse amount =
                        repaymentScheduleConfirmService.confirm(target.getRepaymentScheduleId(), businessDate);
                RepaymentScheduleStatus status = amount.isZero()
                        ? RepaymentScheduleStatus.PAID
                        : RepaymentScheduleStatus.DUE;
                repaymentScheduleMapper.updateStatus(target.getRepaymentScheduleId(), status);
            }
            case UNCONFIRMED_PAST_DUE -> throw new UnconfirmedInstallmentPastDueException(
                    target.getLoanAccountId(), target.getRepaymentScheduleId(),
                    target.getScheduledDate(), businessDate);
            case MARK_PAID, MARK_OVERDUE, MARK_DUE -> {
                if (judgment.getStatus() != target.getRepaymentScheduleStatusId()) {
                    repaymentScheduleMapper.updateStatus(target.getRepaymentScheduleId(), judgment.getStatus());
                }
            }
        }
    }
}
