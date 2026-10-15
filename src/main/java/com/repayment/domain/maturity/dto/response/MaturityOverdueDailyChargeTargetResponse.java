package com.repayment.domain.maturity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 만기 경과 대출의 하루 연체금을 계산하는 데 필요한 값.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaturityOverdueDailyChargeTargetResponse {

    private Long loanMaturityOverdueId;
    private BigDecimal appliedBaseRate;
    private BigDecimal appliedAdditionalRate;
    private BigDecimal loanPrincipalBalance;
}
