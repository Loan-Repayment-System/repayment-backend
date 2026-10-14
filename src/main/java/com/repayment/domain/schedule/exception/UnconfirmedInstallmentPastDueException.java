package com.repayment.domain.schedule.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 예정일이 지났는데 금액이 확정되지 않은 회차다. 연체로 판정하지 않고 그 대출을 멈춘다.
 */
public class UnconfirmedInstallmentPastDueException extends BusinessException {

    private final Long loanAccountId;
    private final Long repaymentScheduleId;
    private final LocalDate scheduledDate;
    private final LocalDate businessDate;

    public UnconfirmedInstallmentPastDueException(Long loanAccountId, Long repaymentScheduleId,
                                                  LocalDate scheduledDate, LocalDate businessDate) {
        super(ErrorCode.UNCONFIRMED_INSTALLMENT_PAST_DUE);
        this.loanAccountId = loanAccountId;
        this.repaymentScheduleId = repaymentScheduleId;
        this.scheduledDate = scheduledDate;
        this.businessDate = businessDate;
    }

    public Long getLoanAccountId() {
        return loanAccountId;
    }

    public Long getRepaymentScheduleId() {
        return repaymentScheduleId;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " loanAccountId=" + loanAccountId + ", repaymentScheduleId=" + repaymentScheduleId
                + ", scheduledDate=" + scheduledDate + ", businessDate=" + businessDate;
    }
}
