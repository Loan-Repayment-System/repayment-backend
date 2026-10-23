package com.repayment.domain.acceleration.dto.response;

import com.repayment.global.code.DelinquencyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 대출 현황 조회의 기한이익상실 하루 기록. 하루 약정이자는 ❻항(판정 13·23)일 때만 0보다 크다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccelerationDailyChargeDetailResponse {

    private LocalDate chargeDate;
    private BigDecimal delinquencyTargetAmount;
    private BigDecimal delinquencyRate;
    private BigDecimal delinquencyCharge;
    private BigDecimal paidDelinquencyCharge;
    private BigDecimal interestAmount;
    private BigDecimal paidInterest;
    private DelinquencyStatus delinquencyStatus;
}
