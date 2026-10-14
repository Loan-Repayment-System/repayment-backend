package com.repayment.domain.loan.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 대출(loan_account 행)이 없다.
 */
public class LoanAccountNotFoundException extends BusinessException {

    private final Long loanAccountId;

    public LoanAccountNotFoundException(Long loanAccountId) {
        super(ErrorCode.LOAN_ACCOUNT_NOT_FOUND);
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
