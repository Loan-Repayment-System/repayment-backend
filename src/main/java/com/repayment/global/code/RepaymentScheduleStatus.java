package com.repayment.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 상환 스케줄(회차) 상태. 코드성 테이블 repayment_schedule_status의 id와 1:1이다.
 */
@Getter
@RequiredArgsConstructor
public enum RepaymentScheduleStatus implements CodeEnum {

    SCHEDULED(1, "예정"),
    DUE(2, "납부 기한 도래"),
    PAID(3, "납입 완료"),
    OVERDUE(4, "연체"),
    CLOSED_BY_ACCELERATION(5, "기한이익상실로 종료"),
    CLOSED_BY_MATURITY_OVERDUE(6, "만기 경과로 종료");

    private final int id;
    private final String description;
}
