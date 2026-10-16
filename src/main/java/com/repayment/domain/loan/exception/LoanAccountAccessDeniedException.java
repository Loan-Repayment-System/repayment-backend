package com.repayment.domain.loan.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 요청한 회원의 대출이 아니다.
 */
public class LoanAccountAccessDeniedException extends BusinessException {

    private final Long loanAccountId;
    private final Long memberId;

    public LoanAccountAccessDeniedException(Long loanAccountId, Long memberId) {
        super(ErrorCode.LOAN_ACCOUNT_ACCESS_DENIED);
        this.loanAccountId = loanAccountId;
        this.memberId = memberId;
    }

    public Long getLoanAccountId() {
        return loanAccountId;
    }

    public Long getMemberId() {
        return memberId;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " loanAccountId=" + loanAccountId + ", memberId=" + memberId;
    }
}
