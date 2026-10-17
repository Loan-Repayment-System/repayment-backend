package com.repayment.domain.closing.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 같은 기준일의 일마감이 이미 '진행중'이다.
 */
public class DailyClosingAlreadyRunningException extends BusinessException {

    private final LocalDate businessDate;

    public DailyClosingAlreadyRunningException(LocalDate businessDate) {
        super(ErrorCode.DAILY_CLOSING_ALREADY_RUNNING);
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
