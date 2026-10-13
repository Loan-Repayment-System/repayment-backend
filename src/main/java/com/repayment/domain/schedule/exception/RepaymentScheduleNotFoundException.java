package com.repayment.domain.schedule.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 확정하려는 회차(repayment_schedule 행)가 없다.
 */
public class RepaymentScheduleNotFoundException extends BusinessException {

    private final Long repaymentScheduleId;

    public RepaymentScheduleNotFoundException(Long repaymentScheduleId) {
        super(ErrorCode.REPAYMENT_SCHEDULE_NOT_FOUND);
        this.repaymentScheduleId = repaymentScheduleId;
    }

    public Long getRepaymentScheduleId() {
        return repaymentScheduleId;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " repaymentScheduleId=" + repaymentScheduleId;
    }
}
