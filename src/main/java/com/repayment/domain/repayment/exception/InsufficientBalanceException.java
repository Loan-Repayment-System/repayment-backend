package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.CollectionRejectedException;

import java.time.LocalDate;

/**
 * 집금 응답 B003. 상환 계좌의 잔액이 상환 금액보다 적다.
 */
public class InsufficientBalanceException extends CollectionRejectedException {

    public InsufficientBalanceException(LocalDate sendDate, int telegramNo) {
        super(ErrorCode.INSUFFICIENT_BALANCE, sendDate, telegramNo);
    }
}
