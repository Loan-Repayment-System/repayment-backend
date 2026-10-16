package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.math.BigDecimal;

/**
 * 상환 금액이 1원 미만이거나 원 단위 정수가 아니다.
 */
public class InvalidRepaymentAmountException extends BusinessException {

    private final BigDecimal amount;

    public InvalidRepaymentAmountException(BigDecimal amount) {
        super(ErrorCode.INVALID_REPAYMENT_AMOUNT);
        this.amount = amount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " amount=" + amount;
    }
}
