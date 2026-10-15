package com.repayment.domain.transfer.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

/**
 * 이체할 계좌(account 행)가 없다. 상환 계좌는 FK로 묶여 있어 데이터 오류일 때만 일어난다.
 */
public class AccountNotFoundException extends BusinessException {

    private final Long accountId;

    public AccountNotFoundException(Long accountId) {
        super(ErrorCode.ACCOUNT_NOT_FOUND);
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " accountId=" + accountId;
    }
}
