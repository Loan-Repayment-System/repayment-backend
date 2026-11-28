package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * DB 장애나 펌뱅킹 장애로 자동 이체 상환을 이어 갈 수 없어 Job을 멈춘다. 원인 DB 오류나 마지막 집금 결과 확인 중 예외는 cause로 남는다.
 * Step의 retry·skip 대상이 아니어야, Spring Batch가 chunk를 다시 써 이미 출금한 대출을 다시 출금하지 않는다.
 */
public class AutoDebitRepaymentAbortedException extends BusinessException {

    private final Long loanAccountId;

    public AutoDebitRepaymentAbortedException(Long loanAccountId, Throwable cause) {
        super(ErrorCode.AUTO_DEBIT_REPAYMENT_ABORTED, cause);
        this.loanAccountId = loanAccountId;
    }

    public Long getLoanAccountId() {
        return loanAccountId;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " loanAccountId=" + loanAccountId;
    }
}
