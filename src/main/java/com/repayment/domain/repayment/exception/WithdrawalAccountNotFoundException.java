package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.CollectionRejectedException;

import java.time.LocalDate;

/**
 * 집금 응답 B001. 상환 계좌가 은행에 없다.
 */
public class WithdrawalAccountNotFoundException extends CollectionRejectedException {

    public WithdrawalAccountNotFoundException(LocalDate sendDate, int telegramNo) {
        super(ErrorCode.WITHDRAWAL_ACCOUNT_NOT_FOUND, sendDate, telegramNo);
    }
}
