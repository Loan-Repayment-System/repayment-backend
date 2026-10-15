package com.repayment.domain.maturity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 만기 경과로 전환할 때 '연체' 회차에서 대출 단위로 옮길 금리와 금액.
 * 밀린 원금은 원금잔액과 같아 옮기지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanMaturityOverdueCarryOverResponse {

    private BigDecimal appliedBaseRate;
    private BigDecimal appliedAdditionalRate;
    private BigDecimal carriedInterest;
    private BigDecimal carriedDelinquencyCharge;
}
