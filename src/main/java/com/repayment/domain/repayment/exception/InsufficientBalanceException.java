package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 집금 응답 B003. 상환 계좌의 잔액이 상환 금액보다 적다.
 */
public class InsufficientBalanceException extends BusinessException {

    private final LocalDate sendDate;
    private final int telegramNo;

    public InsufficientBalanceException(LocalDate sendDate, int telegramNo) {
        super(ErrorCode.INSUFFICIENT_BALANCE);
        this.sendDate = sendDate;
        this.telegramNo = telegramNo;
    }

    public LocalDate getSendDate() {
        return sendDate;
    }

    public int getTelegramNo() {
        return telegramNo;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " sendDate=" + sendDate + " telegramNo=" + telegramNo;
    }
}
