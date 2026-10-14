package com.repayment.domain.schedule.service;

import com.repayment.global.code.RepaymentScheduleStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회차 하나의 판정 결과. 상태를 바꾸는 결과만 바꿀 상태를 가진다.
 */
@Getter
@RequiredArgsConstructor
public enum RepaymentScheduleJudgment {

    KEEP(null),
    CONFIRM(null),
    UNCONFIRMED_PAST_DUE(null),
    PAID(RepaymentScheduleStatus.PAID),
    OVERDUE(RepaymentScheduleStatus.OVERDUE),
    DUE(RepaymentScheduleStatus.DUE);

    private final RepaymentScheduleStatus status;
}
