package com.repayment.domain.schedule.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 회차 확정에 쓸 적용 금리. 단위는 %다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppliedRateResponse {

    private BigDecimal baseRate;
    private BigDecimal additionalRate;
}
