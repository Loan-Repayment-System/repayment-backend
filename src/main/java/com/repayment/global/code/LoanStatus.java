package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 고객에게 보여 주는 대출 상태. 코드성 테이블 loan_status의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum LoanStatus implements CodeEnum {

    NORMAL(1, "정상"),
    OVERDUE(2, "연체"),
    PAID_OFF(3, "완제"),
    ACCELERATED(4, "기한이익상실"),
    MATURITY_OVERDUE(5, "만기 경과");

    private final int id;
    private final String description;
}
