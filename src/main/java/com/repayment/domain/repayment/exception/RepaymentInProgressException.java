package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 대출에 출금 결과를 모르거나 반영 전인 집금 요청이 있다. 새 상환을 받으면 돈이 두 번 빠질 수 있다.
 */
public class RepaymentInProgressException extends BusinessException {

    private final Long loanAccountId;

    public RepaymentInProgressException(Long loanAccountId) {
        super(ErrorCode.REPAYMENT_IN_PROGRESS);
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
