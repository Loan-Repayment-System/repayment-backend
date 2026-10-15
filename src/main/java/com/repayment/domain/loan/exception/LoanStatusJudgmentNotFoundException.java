package com.repayment.domain.loan.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 기준일의 대출 상태 판정이 없다. 가장 최근 판정 행이 없거나 다른 기준일의 행이다.
 */
public class LoanStatusJudgmentNotFoundException extends BusinessException {

    private final Long loanAccountId;
    private final LocalDate businessDate;

    public LoanStatusJudgmentNotFoundException(Long loanAccountId, LocalDate businessDate) {
        super(ErrorCode.LOAN_STATUS_JUDGMENT_NOT_FOUND);
        this.loanAccountId = loanAccountId;
        this.businessDate = businessDate;
    }

    public Long getLoanAccountId() {
        return loanAccountId;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " loanAccountId=" + loanAccountId + ", businessDate=" + businessDate;
    }
}
