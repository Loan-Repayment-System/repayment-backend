package com.repayment.domain.acceleration.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 기한이익상실로 전환한 기록(loan_acceleration 행)이 없다.
 */
public class LoanAccelerationNotFoundException extends BusinessException {

    private final Long loanAccountId;

    public LoanAccelerationNotFoundException(Long loanAccountId) {
        super(ErrorCode.LOAN_ACCELERATION_NOT_FOUND);
        this.loanAccountId = loanAccountId;
    }

    public Long getLoanAccountId() {
        return loanAccountId;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " loanAccountId=" + loanAccountId;
    }
}
