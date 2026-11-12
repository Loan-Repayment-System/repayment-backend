package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 집금 응답 B001. 상환 계좌가 은행에 없다.
 */
public class WithdrawalAccountNotFoundException extends BusinessException {

    private final LocalDate sendDate;
    private final int telegramNo;

    public WithdrawalAccountNotFoundException(LocalDate sendDate, int telegramNo) {
        super(ErrorCode.WITHDRAWAL_ACCOUNT_NOT_FOUND);
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
