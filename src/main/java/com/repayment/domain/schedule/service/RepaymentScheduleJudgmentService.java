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
 * 회차를 기준일에 맞게 판정하고, 결과대로 확정하거나 상태를 바꾼다.
 * 일마감은 대출 단위(judgeLoan)로, 상환은 충당으로 바뀔 회차만 골라 회차 단위(judge)로 부른다.
 */
@Service
@RequiredArgsConstructor
public class RepaymentScheduleJudgmentService {

    private final RepaymentScheduleJudge repaymentScheduleJudge;
    private final RepaymentScheduleConfirmService repaymentScheduleConfirmService;
    private final RepaymentScheduleMapper repaymentScheduleMapper;

    /**
     * 대출 하나의 끝나지 않은 회차(예정·납부 기한 도래·연체)를 회차 번호 순으로 판정한다.
     * 앞 회차부터 확정해야 하므로 순서를 지킨다. 한 회차에서 예외가 나면 거기서 멈춘다.
     */
    @Transactional
    public void judgeLoan(Long loanAccountId, LocalDate businessDate) {
        for (RepaymentScheduleJudgmentTargetResponse target
                : repaymentScheduleMapper.findUnfinishedJudgmentTargets(loanAccountId)) {
            judge(target, businessDate);
        }
    }

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
