package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 일마감 실행 상태. 코드성 테이블 daily_closing_status의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum DailyClosingStatus implements CodeEnum {

    IN_PROGRESS(1, "진행중"),
    COMPLETED(2, "완료"),
    FAILED(3, "실패");

    private final int id;
    private final String description;
}
