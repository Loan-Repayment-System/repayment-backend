package com.repayment.domain.closing.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 전날 일마감이 '완료'가 아니다. 빠진 날은 그날부터 순서대로 실행한다.
 */
public class PreviousDailyClosingNotCompletedException extends BusinessException {

    private final LocalDate businessDate;

    public PreviousDailyClosingNotCompletedException(LocalDate businessDate) {
        super(ErrorCode.PREVIOUS_DAILY_CLOSING_NOT_COMPLETED);
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
