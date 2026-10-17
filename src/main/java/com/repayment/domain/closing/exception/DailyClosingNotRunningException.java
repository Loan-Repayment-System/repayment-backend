package com.repayment.domain.closing.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 기준일의 일마감이 '진행중'이 아니라 완료로 바꿀 수 없다.
 */
public class DailyClosingNotRunningException extends BusinessException {

    private final LocalDate businessDate;

    public DailyClosingNotRunningException(LocalDate businessDate) {
        super(ErrorCode.DAILY_CLOSING_NOT_RUNNING);
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
