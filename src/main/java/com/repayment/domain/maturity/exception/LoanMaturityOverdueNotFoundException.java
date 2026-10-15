package com.repayment.domain.maturity.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 만기 경과로 전환한 기록(loan_maturity_overdue 행)이 없다.
 */
public class LoanMaturityOverdueNotFoundException extends BusinessException {

    private final Long loanAccountId;

    public LoanMaturityOverdueNotFoundException(Long loanAccountId) {
        super(ErrorCode.LOAN_MATURITY_OVERDUE_NOT_FOUND);
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
