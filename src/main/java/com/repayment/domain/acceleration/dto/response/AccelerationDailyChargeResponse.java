package com.repayment.domain.acceleration.dto.response;

import com.repayment.global.code.DelinquencyStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 기한이익상실 대출에 그날 쌓을 연체금과 하루 약정이자.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccelerationDailyChargeResponse {

    private Long loanAccelerationId;
    private LocalDate chargeDate;
    private LoanJudgmentStatus loanJudgmentStatusId;
    private BigDecimal delinquencyTargetAmount;
    private BigDecimal delinquencyRate;
    private BigDecimal delinquencyCharge;
    private BigDecimal interestAmount;
    private DelinquencyStatus delinquencyStatusId;
}
