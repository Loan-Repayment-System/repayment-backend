package com.repayment.domain.acceleration.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 기한이익상실 대출의 하루 연체금·약정이자를 계산하는 데 필요한 값.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccelerationDailyChargeTargetResponse {

    private Long loanAccelerationId;
    private BigDecimal appliedBaseRate;
    private BigDecimal appliedAdditionalRate;
    private BigDecimal carriedInterest;
    private BigDecimal paidCarriedInterest;
    private BigDecimal carriedOverduePrincipal;
    private BigDecimal paidCarriedOverduePrincipal;
    private BigDecimal loanPrincipalBalance;
}
