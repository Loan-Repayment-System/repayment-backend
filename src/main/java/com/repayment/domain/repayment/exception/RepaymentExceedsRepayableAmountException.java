package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.math.BigDecimal;

/**
 * 상환 금액이 갚을 금액보다 많다. 실패 응답의 data에 갚을 금액을 담는다.
 */
public class RepaymentExceedsRepayableAmountException extends BusinessException {

    private final Long loanAccountId;
    private final BigDecimal amount;
    private final BigDecimal repayableAmount;

    public RepaymentExceedsRepayableAmountException(Long loanAccountId, BigDecimal amount, BigDecimal repayableAmount) {
        super(ErrorCode.REPAYMENT_EXCEEDS_REPAYABLE_AMOUNT, repayableAmount);
        this.loanAccountId = loanAccountId;
        this.amount = amount;
        this.repayableAmount = repayableAmount;
    }

    public Long getLoanAccountId() {
        return loanAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getRepayableAmount() {
        return repayableAmount;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " loanAccountId=" + loanAccountId
                + ", amount=" + amount + ", repayableAmount=" + repayableAmount;
    }
}
