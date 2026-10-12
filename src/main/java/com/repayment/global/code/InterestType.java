package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이자 유형. 코드성 테이블 interest_type의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum InterestType implements CodeEnum {

    FIXED(1, "고정금리"),
    VARIABLE(2, "변동금리");

    private final int id;
    private final String description;
}
