package com.repayment.domain.delinquency.dto.response;

import com.repayment.global.code.DelinquencyStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 회차 하나에 그날 쌓을 연체금.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentDelinquencyChargeResponse {

    private Long repaymentScheduleId;
    private LocalDate chargeDate;
    private LoanJudgmentStatus loanJudgmentStatusId;
    private BigDecimal delinquencyTargetAmount;
    private BigDecimal delinquencyRate;
    private BigDecimal delinquencyCharge;
    private DelinquencyStatus delinquencyStatusId;
}
