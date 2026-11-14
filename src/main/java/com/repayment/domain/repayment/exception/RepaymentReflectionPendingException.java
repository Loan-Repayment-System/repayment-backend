package com.repayment.domain.repayment.exception;

import com.repayment.domain.repayment.dto.response.RepaymentPendingResponse;
import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 집금은 성공했는데 상환 반영(기록·충당·판정)에 실패했다. 집금 요청은 집금 완료 상태로 남는다.
 */
public class RepaymentReflectionPendingException extends BusinessException {

    private final String transactionNo;

    public RepaymentReflectionPendingException(String transactionNo, Throwable cause) {
        super(ErrorCode.REPAYMENT_REFLECTION_PENDING, new RepaymentPendingResponse(transactionNo), cause);
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
