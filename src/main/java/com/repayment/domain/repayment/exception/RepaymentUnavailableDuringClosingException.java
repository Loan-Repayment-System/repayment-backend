package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 기준일의 일마감이 '완료'가 아니라 상환을 받을 수 없다. 행이 없거나 진행중·실패다.
 */
public class RepaymentUnavailableDuringClosingException extends BusinessException {

    private final LocalDate businessDate;

    public RepaymentUnavailableDuringClosingException(LocalDate businessDate) {
        super(ErrorCode.REPAYMENT_UNAVAILABLE_DURING_CLOSING);
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
