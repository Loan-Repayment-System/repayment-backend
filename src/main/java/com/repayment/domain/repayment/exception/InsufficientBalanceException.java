package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 집금 응답 B003. 상환 계좌의 잔액이 상환 금액보다 적다.
 */
public class InsufficientBalanceException extends BusinessException {

    private final String transactionNo;

    public InsufficientBalanceException(String transactionNo) {
        super(ErrorCode.INSUFFICIENT_BALANCE);
        this.transactionNo = transactionNo;
    }

    public String getTransactionNo() {
        return transactionNo;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " transactionNo=" + transactionNo;
    }
}
