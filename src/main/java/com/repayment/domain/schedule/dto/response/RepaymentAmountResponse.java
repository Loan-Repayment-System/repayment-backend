package com.repayment.domain.schedule.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 회차 하나의 원금과 이자. 둘 다 원 미만을 버린 값이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentAmountResponse {

    private BigDecimal principal;
    private BigDecimal interest;

    public boolean isZero() {
        return principal.add(interest).signum() == 0;
    }
}
