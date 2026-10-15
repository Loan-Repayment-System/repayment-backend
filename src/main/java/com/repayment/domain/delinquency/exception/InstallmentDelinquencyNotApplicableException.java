package com.repayment.domain.delinquency.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.exception.BusinessException;

/**
 * 회차 연체금은 11 만기일시 이자연체·21 분할상환 1회 연체일 때만 쌓는다. 다른 판정으로 불렀다.
 */
public class InstallmentDelinquencyNotApplicableException extends BusinessException {

    private final Long loanAccountId;
    private final LoanJudgmentStatus todayJudgment;

    public InstallmentDelinquencyNotApplicableException(Long loanAccountId, LoanJudgmentStatus todayJudgment) {
        super(ErrorCode.INSTALLMENT_DELINQUENCY_NOT_APPLICABLE);
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
