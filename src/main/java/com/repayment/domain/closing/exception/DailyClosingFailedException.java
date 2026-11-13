package com.repayment.domain.closing.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 일마감 Job이 업무 예외가 아닌 원인으로 완료되지 못했다. 원인은 cause로 남는다.
 */
public class DailyClosingFailedException extends BusinessException {

    private final LocalDate businessDate;

    public DailyClosingFailedException(LocalDate businessDate, Throwable cause) {
        super(ErrorCode.DAILY_CLOSING_FAILED, cause);
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
