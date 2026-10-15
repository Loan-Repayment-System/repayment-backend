package com.repayment.domain.maturity.dto.response;

import com.repayment.global.code.DelinquencyStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 만기 경과 대출에 그날 쌓을 연체금. 하루 약정이자는 붙지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaturityOverdueDailyChargeResponse {

    private Long loanMaturityOverdueId;
    private LocalDate chargeDate;
    private LoanJudgmentStatus loanJudgmentStatusId;
    private BigDecimal delinquencyTargetAmount;
    private BigDecimal delinquencyRate;
    private BigDecimal delinquencyCharge;
    private DelinquencyStatus delinquencyStatusId;
}
