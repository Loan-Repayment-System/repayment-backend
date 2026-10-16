package com.repayment.domain.repayment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 상환 1건이 회차 하나에 충당한 금액. paid_*는 이 상환의 금액이라 누적이 아니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentRepaymentResponse {

    private Long repaymentId;
    private Long repaymentScheduleId;
    private BigDecimal paidPrincipal;
    private BigDecimal paidInterest;
    private BigDecimal paidDelinquencyCharge;
}
