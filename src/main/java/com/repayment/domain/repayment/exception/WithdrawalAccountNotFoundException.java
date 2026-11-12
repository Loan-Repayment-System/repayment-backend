package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 집금 응답 B001. 상환 계좌가 은행에 없다.
 */
public class WithdrawalAccountNotFoundException extends BusinessException {

    private final String transactionNo;

    public WithdrawalAccountNotFoundException(String transactionNo) {
        super(ErrorCode.WITHDRAWAL_ACCOUNT_NOT_FOUND);
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
