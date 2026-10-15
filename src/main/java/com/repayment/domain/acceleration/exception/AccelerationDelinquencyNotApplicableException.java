package com.repayment.domain.acceleration.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.exception.BusinessException;

/**
 * 기한이익상실 연체금은 12·13·22·23 기한이익상실일 때만 쌓는다. 다른 판정으로 불렀다.
 */
public class AccelerationDelinquencyNotApplicableException extends BusinessException {

    private final Long loanAccountId;
    private final LoanJudgmentStatus todayJudgment;

    public AccelerationDelinquencyNotApplicableException(Long loanAccountId, LoanJudgmentStatus todayJudgment) {
        super(ErrorCode.ACCELERATION_DELINQUENCY_NOT_APPLICABLE);
        this.loanAccountId = loanAccountId;
        this.todayJudgment = todayJudgment;
    }

    public Long getLoanAccountId() {
        return loanAccountId;
    }

    public LoanJudgmentStatus getTodayJudgment() {
        return todayJudgment;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " loanAccountId=" + loanAccountId + ", todayJudgment=" + todayJudgment;
    }
}
