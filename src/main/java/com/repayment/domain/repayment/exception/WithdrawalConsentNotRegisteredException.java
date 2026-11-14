package com.repayment.domain.repayment.exception;

import com.repayment.global.code.ErrorCode;
import com.repayment.global.exception.CollectionRejectedException;

import java.time.LocalDate;

/**
 * 집금 응답 C001. 상환 계좌의 출금 동의가 펌뱅킹에 등록되어 있지 않다.
 */
public class WithdrawalConsentNotRegisteredException extends CollectionRejectedException {

    public WithdrawalConsentNotRegisteredException(LocalDate sendDate, int telegramNo) {
        super(ErrorCode.WITHDRAWAL_CONSENT_NOT_REGISTERED, sendDate, telegramNo);
    }
}
