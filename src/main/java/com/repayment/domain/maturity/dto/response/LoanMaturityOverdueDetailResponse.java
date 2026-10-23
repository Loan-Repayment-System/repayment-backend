package com.repayment.domain.maturity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 대출 현황 조회의 만기 경과 기록과 전환 뒤 하루 연체금.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanMaturityOverdueDetailResponse {

    private LocalDate convertedDate;
    private BigDecimal appliedBaseRate;
    private BigDecimal appliedAdditionalRate;
    private BigDecimal carriedInterest;
    private BigDecimal paidCarriedInterest;
    private BigDecimal carriedDelinquencyCharge;
    private BigDecimal paidCarriedDelinquencyCharge;

    @Builder.Default
    private List<MaturityOverdueDailyChargeDetailResponse> dailyCharges = new ArrayList<>();
}
