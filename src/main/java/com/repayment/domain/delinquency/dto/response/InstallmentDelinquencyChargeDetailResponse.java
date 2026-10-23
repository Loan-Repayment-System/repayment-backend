package com.repayment.domain.delinquency.dto.response;

import com.repayment.global.code.DelinquencyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 대출 현황 조회의 회차 하루 연체금.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentDelinquencyChargeDetailResponse {

    private LocalDate chargeDate;
    private BigDecimal delinquencyTargetAmount;
    private BigDecimal delinquencyRate;
    private BigDecimal delinquencyCharge;
    private BigDecimal paidDelinquencyCharge;
    private DelinquencyStatus delinquencyStatus;
}
