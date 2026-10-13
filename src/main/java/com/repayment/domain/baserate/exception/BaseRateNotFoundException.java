package com.repayment.domain.baserate.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 기준 날짜보다 앞서 공시된 기준금리가 base_rate_history에 없다.
 */
public class BaseRateNotFoundException extends BusinessException {

    private final LocalDate referenceDate;

    public BaseRateNotFoundException(LocalDate referenceDate) {
        super(ErrorCode.BASE_RATE_NOT_FOUND);
        this.referenceDate = referenceDate;
    }

    public LocalDate getReferenceDate() {
        return referenceDate;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " referenceDate=" + referenceDate;
    }
}
