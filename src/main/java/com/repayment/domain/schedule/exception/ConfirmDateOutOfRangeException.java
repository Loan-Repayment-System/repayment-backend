package com.repayment.domain.schedule.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 기준일이 '예정일 − 5일' ~ 예정일 밖이다.
 */
public class ConfirmDateOutOfRangeException extends BusinessException {

    private final Long repaymentScheduleId;
    private final LocalDate businessDate;
    private final LocalDate scheduledDate;

    public ConfirmDateOutOfRangeException(Long repaymentScheduleId, LocalDate businessDate, LocalDate scheduledDate) {
        super(ErrorCode.INVALID_CONFIRM_DATE);
        this.repaymentScheduleId = repaymentScheduleId;
        this.businessDate = businessDate;
        this.scheduledDate = scheduledDate;
    }

    public Long getRepaymentScheduleId() {
        return repaymentScheduleId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " repaymentScheduleId=" + repaymentScheduleId
                + ", businessDate=" + businessDate + ", scheduledDate=" + scheduledDate;
    }
}
