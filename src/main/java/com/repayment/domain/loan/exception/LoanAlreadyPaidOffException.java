package com.repayment.domain.loan.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 가장 최근 판정이 2 완제라 더 갚을 수 없다.
 */
public class LoanAlreadyPaidOffException extends BusinessException {

    private final Long loanAccountId;

    public LoanAlreadyPaidOffException(Long loanAccountId) {
        super(ErrorCode.LOAN_ALREADY_PAID_OFF);
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
