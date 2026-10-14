package com.repayment.domain.schedule.dto.response;

import com.repayment.global.code.RepaymentScheduleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회차 하나의 상태를 판정하는 데 필요한 값. 미확정 회차는 금액이 비어 있다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentScheduleJudgmentTargetResponse {

    private Long repaymentScheduleId;
    private Long loanAccountId;
    private RepaymentScheduleStatus repaymentScheduleStatusId;
    private LocalDate scheduledDate;
    private LocalDateTime confirmedAt;
    private BigDecimal scheduledPrincipal;
    private BigDecimal scheduledInterest;
    private BigDecimal paidPrincipal;
    private BigDecimal paidInterest;
}
