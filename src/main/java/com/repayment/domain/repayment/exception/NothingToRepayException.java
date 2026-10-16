package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 완제는 아니지만 지금 갚을 금액이 0원이다. 예) 0 정상인데 '납부 기한 도래' 회차가 없다.
 */
public class NothingToRepayException extends BusinessException {

    private final Long loanAccountId;

    public NothingToRepayException(Long loanAccountId) {
        super(ErrorCode.NOTHING_TO_REPAY);
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
