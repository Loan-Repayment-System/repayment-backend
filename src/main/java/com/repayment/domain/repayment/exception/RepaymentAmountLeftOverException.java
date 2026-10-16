package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.math.BigDecimal;

/**
 * 목록을 다 채우고도 납부 금액이 남았다. 상환 가능 확인이 초과를 거절하므로 갚을 금액과 충당이 어긋났을 때만 생긴다.
 */
public class RepaymentAmountLeftOverException extends BusinessException {

    private final Long loanAccountId;
    private final BigDecimal leftOverAmount;

    public RepaymentAmountLeftOverException(Long loanAccountId, BigDecimal leftOverAmount) {
        super(ErrorCode.REPAYMENT_AMOUNT_LEFT_OVER);
        this.loanAccountId = loanAccountId;
        this.leftOverAmount = leftOverAmount;
    }

    public Long getLoanAccountId() {
        return loanAccountId;
    }

    public BigDecimal getLeftOverAmount() {
        return leftOverAmount;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " loanAccountId=" + loanAccountId + ", leftOverAmount=" + leftOverAmount;
    }
}
