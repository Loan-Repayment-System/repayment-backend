package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 자동 이체 상환 Job이 완료되지 못했다. 원인은 cause로 남는다.
 */
public class AutoDebitRepaymentFailedException extends BusinessException {

    private final LocalDate businessDate;

    public AutoDebitRepaymentFailedException(LocalDate businessDate, Throwable cause) {
        super(ErrorCode.AUTO_DEBIT_REPAYMENT_FAILED, cause);
        this.businessDate = businessDate;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " businessDate=" + businessDate;
    }
}
