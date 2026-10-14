package com.repayment.domain.schedule.service;

import com.repayment.global.code.RepaymentScheduleStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 회차 하나의 판정 결과. MARK_로 시작하는 결과만 바꿀 회차 상태를 가진다.
 */
@Getter
@RequiredArgsConstructor
public enum RepaymentScheduleJudgment {

    KEEP(null),
    CONFIRM(null),
    UNCONFIRMED_PAST_DUE(null),
    MARK_PAID(RepaymentScheduleStatus.PAID),
    MARK_OVERDUE(RepaymentScheduleStatus.OVERDUE),
    MARK_DUE(RepaymentScheduleStatus.DUE);

    private final RepaymentScheduleStatus status;
}
