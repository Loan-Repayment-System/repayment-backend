package com.repayment.domain.baserate.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 기준금리를 조회하지 못했다. reason은 processing_failure의 사유로 남길 내용이다.
 */
public class BaseRateFetchFailedException extends BusinessException {

    private final String reason;

    public BaseRateFetchFailedException(String reason) {
        super(ErrorCode.BASE_RATE_FETCH_FAILED);
        this.reason = reason;
    }

    public BaseRateFetchFailedException(String reason, Throwable cause) {
        super(ErrorCode.BASE_RATE_FETCH_FAILED, cause);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " " + reason;
    }
}
