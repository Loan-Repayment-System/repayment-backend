package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 집금이 고객이 고칠 수 없는 이유로 실패했다. 응답코드가 0000·B001·B003·C001이 아니거나, 응답을 받지 못했다.
 * 응답을 받지 못하면 펌뱅킹이 처리했는지 알 수 없지만 지금은 실패로 본다.
 */
public class CollectionFailedException extends BusinessException {

    private final String transactionNo;
    private final String responseCode;

    public CollectionFailedException(String transactionNo, String responseCode) {
        super(ErrorCode.COLLECTION_FAILED);
        this.transactionNo = transactionNo;
        this.responseCode = responseCode;
    }

    public CollectionFailedException(String transactionNo, Throwable cause) {
        super(ErrorCode.COLLECTION_FAILED, cause);
        this.transactionNo = transactionNo;
        this.responseCode = null;
    }

    public String getTransactionNo() {
        return transactionNo;
    }

    public String getResponseCode() {
        return responseCode;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " transactionNo=" + transactionNo + " responseCode=" + responseCode;
    }
}
