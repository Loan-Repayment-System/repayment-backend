package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 판정 주체. 코드성 테이블 judgment_source의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum JudgmentSource implements CodeEnum {

    DAILY_CLOSING(1, "일마감"),
    REPAYMENT(2, "상환");

    private final int id;
    private final String description;
}
