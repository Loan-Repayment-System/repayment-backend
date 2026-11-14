package com.repayment.global.exception;

import com.repayment.global.code.ErrorCode;

import java.time.LocalDate;

/**
 * 펌뱅킹이 응답코드로 집금을 거절했다. 이체가 일어나지 않은 것이 확실한 실패를 한 타입으로 잡는다.
 */
public abstract class CollectionRejectedException extends BusinessException {

    private final LocalDate sendDate;
    private final int telegramNo;

    protected CollectionRejectedException(ErrorCode errorCode, LocalDate sendDate, int telegramNo) {
        super(errorCode);
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
