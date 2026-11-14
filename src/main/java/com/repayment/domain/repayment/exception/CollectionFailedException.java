package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.CollectionRejectedException;

import java.time.LocalDate;

/**
 * 집금이 고객이 고칠 수 없는 이유로 거절됐다. 응답코드가 0000·B001·B003·C001이 아니다.
 */
public class CollectionFailedException extends CollectionRejectedException {

    private final String responseCode;

    public CollectionFailedException(LocalDate sendDate, int telegramNo, String responseCode) {
        super(ErrorCode.COLLECTION_FAILED, sendDate, telegramNo);
        this.responseCode = responseCode;
    }

    public String getResponseCode() {
        return responseCode;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " responseCode=" + responseCode;
    }
}
