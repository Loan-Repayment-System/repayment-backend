package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 기준금리 지표. 코드성 테이블 base_rate_index의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum BaseRateIndex implements CodeEnum {

    KORIBOR_12M(1, "KORIBOR 12개월");

    private final int id;
    private final String description;
}
