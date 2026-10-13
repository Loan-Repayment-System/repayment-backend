package com.repayment.domain.schedule.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.code.RepaymentScheduleStatus;
import com.repayment.global.exception.BusinessException;

/**
 * 회차가 '예정' 상태가 아니다. 이미 확정했거나 기한이익상실·만기 경과로 닫힌 회차다.
 */
public class RepaymentScheduleNotConfirmableException extends BusinessException {

    private final Long repaymentScheduleId;
    private final RepaymentScheduleStatus status;

    public RepaymentScheduleNotConfirmableException(Long repaymentScheduleId, RepaymentScheduleStatus status) {
        super(ErrorCode.REPAYMENT_SCHEDULE_NOT_CONFIRMABLE);
        this.repaymentScheduleId = repaymentScheduleId;
        this.status = status;
    }

    public Long getRepaymentScheduleId() {
        return repaymentScheduleId;
    }

    public RepaymentScheduleStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " repaymentScheduleId=" + repaymentScheduleId + ", status=" + status;
    }
}
