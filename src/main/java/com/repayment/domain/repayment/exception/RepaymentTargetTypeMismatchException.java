package com.repayment.domain.repayment.exception;

import com.repayment.domain.repayment.service.RepaymentTargetType;
import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 충당 방식에 맞지 않는 종류의 대상이 목록에 있다. 예) 회차 충당에 기한이익상실 대상이 넘어왔다.
 */
public class RepaymentTargetTypeMismatchException extends BusinessException {

    private final Long loanAccountId;
    private final RepaymentTargetType type;

    public RepaymentTargetTypeMismatchException(Long loanAccountId, RepaymentTargetType type) {
        super(ErrorCode.REPAYMENT_TARGET_TYPE_MISMATCH);
        this.loanAccountId = loanAccountId;
        this.type = type;
    }

    public Long getLoanAccountId() {
        return loanAccountId;
    }

    public RepaymentTargetType getType() {
        return type;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " loanAccountId=" + loanAccountId + ", type=" + type;
    }
}
