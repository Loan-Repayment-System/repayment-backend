package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 상환 방법. 코드성 테이블 repayment_method의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum RepaymentMethod implements CodeEnum {

    BULLET(1, "원금만기일시상환"),
    EQUAL_PRINCIPAL_INTEREST(2, "원리금균등상환"),
    EQUAL_PRINCIPAL(3, "원금균등상환");

    private final int id;
    private final String description;
}
