package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계좌 상태. 코드성 테이블 account_status의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum AccountStatus implements CodeEnum {

    ACTIVE(1, "정상"),
    CLOSED(2, "해지"),
    SUSPENDED(3, "정지");

    private final int id;
    private final String description;
}
