package com.repayment.domain.repayment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 기한이익상실로 옮긴 금액의 남은 부분과 대출 원금잔액.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnpaidAccelerationResponse {

    private Long loanAccelerationId;
    private BigDecimal unpaidCarriedDelinquencyCharge;
    private BigDecimal unpaidCarriedInterest;
    private BigDecimal unpaidCarriedOverduePrincipal;
    private BigDecimal loanPrincipalBalance;
}
