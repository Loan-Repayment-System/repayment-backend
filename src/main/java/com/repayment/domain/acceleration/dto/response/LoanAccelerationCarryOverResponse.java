package com.repayment.domain.acceleration.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 기한이익상실로 전환할 때 '연체' 회차에서 대출 단위로 옮길 금리와 금액.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanAccelerationCarryOverResponse {

    private BigDecimal appliedBaseRate;
    private BigDecimal appliedAdditionalRate;
    private BigDecimal carriedInterest;
    private BigDecimal carriedOverduePrincipal;
    private BigDecimal carriedDelinquencyCharge;
}
