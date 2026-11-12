package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.BusinessException;

import java.time.LocalDate;

/**
 * 집금 응답 C001. 상환 계좌의 출금 동의가 펌뱅킹에 등록되어 있지 않다.
 */
public class WithdrawalConsentNotRegisteredException extends BusinessException {

    private final LocalDate sendDate;
    private final int telegramNo;

    public WithdrawalConsentNotRegisteredException(LocalDate sendDate, int telegramNo) {
        super(ErrorCode.WITHDRAWAL_CONSENT_NOT_REGISTERED);
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
