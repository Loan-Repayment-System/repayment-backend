package com.repayment.domain.repayment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 남은 금액이 있는 '연체'·'납부 기한 도래' 회차.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnpaidInstallmentResponse {

    private Long repaymentScheduleId;
    private BigDecimal unpaidInterest;
    private BigDecimal unpaidPrincipal;
}
