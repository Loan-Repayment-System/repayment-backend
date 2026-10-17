package com.repayment.domain.loan.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 상환 계좌가 없거나 그 회원의 계좌가 아니다. 은행 계좌(member_id NULL)도 여기에 해당한다.
 */
public class RepaymentAccountNotOwnedException extends BusinessException {

    private final Long repaymentAccountId;
    private final Long memberId;

    public RepaymentAccountNotOwnedException(Long repaymentAccountId, Long memberId) {
        super(ErrorCode.REPAYMENT_ACCOUNT_NOT_OWNED);
        this.repaymentAccountId = repaymentAccountId;
        this.memberId = memberId;
    }

    public Long getRepaymentAccountId() {
        return repaymentAccountId;
    }

    public Long getMemberId() {
        return memberId;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " repaymentAccountId=" + repaymentAccountId + ", memberId=" + memberId;
    }
}
