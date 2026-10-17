package com.repayment.domain.closing.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 이미 '완료'된 기준일이다. 완료된 날은 다시 실행하지 않는다.
 */
public class DailyClosingAlreadyCompletedException extends BusinessException {

    private final LocalDate businessDate;

    public DailyClosingAlreadyCompletedException(LocalDate businessDate) {
        super(ErrorCode.DAILY_CLOSING_ALREADY_COMPLETED);
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
