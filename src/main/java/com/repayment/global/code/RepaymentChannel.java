package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 상환 경로. 코드성 테이블 repayment_channel의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum RepaymentChannel implements CodeEnum {

    CUSTOMER(1, "고객 상환"),
    AUTO(2, "자동 상환");

    private final int id;
    private final String description;
}
