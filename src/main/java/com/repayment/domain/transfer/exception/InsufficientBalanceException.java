package com.repayment.domain.transfer.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.math.BigDecimal;

/**
 * 출금 계좌의 잔액이 이체 금액보다 적다. 있는 만큼만 옮기지 않는다.
 */
public class InsufficientBalanceException extends BusinessException {

    private final Long accountId;
    private final BigDecimal balance;
    private final BigDecimal amount;

    public InsufficientBalanceException(Long accountId, BigDecimal balance, BigDecimal amount) {
        super(ErrorCode.INSUFFICIENT_BALANCE);
        this.accountId = accountId;
        this.balance = balance;
        this.amount = amount;
    }

    public Long getAccountId() {
        return accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " accountId=" + accountId + ", balance=" + balance + ", amount=" + amount;
    }
}
