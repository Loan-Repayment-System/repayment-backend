package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 설정한 계좌번호의 은행 수납 계좌(account 행)가 없다. 설정이나 데이터 오류일 때만 일어난다.
 */
public class CollectionAccountNotFoundException extends BusinessException {

    private final String accountNo;

    public CollectionAccountNotFoundException(String accountNo) {
        super(ErrorCode.COLLECTION_ACCOUNT_NOT_FOUND);
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
