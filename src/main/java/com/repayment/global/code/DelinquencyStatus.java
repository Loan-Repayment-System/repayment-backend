package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 연체금 상태. 코드성 테이블 delinquency_status의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum DelinquencyStatus implements CodeEnum {

    IN_PROGRESS(1, "진행중"),
    RESOLVED(2, "해소");

    private final int id;
    private final String description;
}
