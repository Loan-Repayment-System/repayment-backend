package com.repayment.domain.repayment.exception;

import com.repayment.domain.repayment.dto.response.RepaymentPendingResponse;
import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 집금 응답을 받지 못해 펌뱅킹이 이체했는지 알 수 없다. 집금 요청은 요청 상태로 남는다.
 */
public class CollectionResultPendingException extends BusinessException {

    private final String transactionNo;

    public CollectionResultPendingException(String transactionNo, Throwable cause) {
        super(ErrorCode.COLLECTION_RESULT_PENDING, new RepaymentPendingResponse(transactionNo), cause);
        this.transactionNo = transactionNo;
    }

    public String getTransactionNo() {
        return transactionNo;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " transactionNo=" + transactionNo;
    }
}
