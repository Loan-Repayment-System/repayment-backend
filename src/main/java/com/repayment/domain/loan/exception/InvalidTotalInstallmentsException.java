package com.repayment.domain.loan.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 회차 수가 1 미만이다.
 */
public class InvalidTotalInstallmentsException extends BusinessException {

    private final int totalInstallments;

    public InvalidTotalInstallmentsException(int totalInstallments) {
        super(ErrorCode.INVALID_TOTAL_INSTALLMENTS);
        this.totalInstallments = totalInstallments;
    }

    public int getTotalInstallments() {
        return totalInstallments;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " totalInstallments=" + totalInstallments;
    }
}
