package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 거래 유형. 코드성 테이블 transaction_type의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum TransactionType implements CodeEnum {

    WITHDRAWAL(1, "출금"),
    DEPOSIT(2, "입금");

    private final int id;
    private final String description;
}
