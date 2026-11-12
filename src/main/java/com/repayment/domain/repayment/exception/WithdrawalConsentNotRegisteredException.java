package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 집금 응답 C001. 상환 계좌의 출금 동의가 펌뱅킹에 등록되어 있지 않다.
 */
public class WithdrawalConsentNotRegisteredException extends BusinessException {

    private final String transactionNo;

    public WithdrawalConsentNotRegisteredException(String transactionNo) {
        super(ErrorCode.WITHDRAWAL_CONSENT_NOT_REGISTERED);
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
