package com.repayment.domain.loan.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 설정한 계좌번호의 지급 계좌(account 행)가 없다. 환경마다 이 계좌를 두어야 한다.
 */
public class DisbursementAccountNotFoundException extends BusinessException {

    private final String accountNo;

    public DisbursementAccountNotFoundException(String accountNo) {
        super(ErrorCode.DISBURSEMENT_ACCOUNT_NOT_FOUND);
        this.accountNo = accountNo;
    }

    public String getAccountNo() {
        return accountNo;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " accountNo=" + accountNo;
    }
}
