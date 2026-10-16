package com.repayment.domain.repayment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 남은 금액이 있는 하루 기록(회차 연체금, 기한이익상실·만기 경과 하루 기록) 한 행.
 * 회차 연체금과 만기 경과 하루 기록은 약정이자가 없어 unpaidInterest가 0이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnpaidDailyChargeResponse {

    private Long chargeId;
    private BigDecimal unpaidDelinquencyCharge;
    private BigDecimal unpaidInterest;
}
