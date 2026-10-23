package com.repayment.domain.acceleration.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 대출 현황 조회의 기한이익상실 기록과 전환 뒤 하루 기록.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanAccelerationDetailResponse {

    private LocalDate convertedDate;
    private BigDecimal appliedBaseRate;
    private BigDecimal appliedAdditionalRate;
    private BigDecimal carriedInterest;
    private BigDecimal paidCarriedInterest;
    private BigDecimal carriedOverduePrincipal;
    private BigDecimal paidCarriedOverduePrincipal;
    private BigDecimal carriedDelinquencyCharge;
    private BigDecimal paidCarriedDelinquencyCharge;

    @Builder.Default
    private List<AccelerationDailyChargeDetailResponse> dailyCharges = new ArrayList<>();
}
