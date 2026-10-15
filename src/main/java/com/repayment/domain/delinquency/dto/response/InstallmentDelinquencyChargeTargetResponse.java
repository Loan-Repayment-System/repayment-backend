package com.repayment.domain.delinquency.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 그날 연체금을 쌓을 '연체' 회차 하나의 금액과 적용 금리.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentDelinquencyChargeTargetResponse {

    private Long repaymentScheduleId;
    private BigDecimal scheduledPrincipal;
    private BigDecimal scheduledInterest;
    private BigDecimal paidPrincipal;
    private BigDecimal paidInterest;
    private BigDecimal appliedBaseRate;
    private BigDecimal appliedAdditionalRate;
}
