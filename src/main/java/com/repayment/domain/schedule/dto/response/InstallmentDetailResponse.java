package com.repayment.domain.schedule.dto.response;

import com.repayment.domain.delinquency.dto.response.InstallmentDelinquencyChargeDetailResponse;
import com.repayment.global.code.RepaymentScheduleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 대출 현황 조회의 회차 하나. 금액이 확정되기 전에는 금액·금리·확정 일시가 null이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentDetailResponse {

    private Integer installmentNo;
    private RepaymentScheduleStatus status;
    private LocalDate scheduledDate;
    private BigDecimal scheduledPrincipal;
    private BigDecimal scheduledInterest;
    private BigDecimal appliedBaseRate;
    private BigDecimal appliedAdditionalRate;
    private LocalDateTime confirmedAt;
    private BigDecimal paidPrincipal;
    private BigDecimal paidInterest;

    @Builder.Default
    private List<InstallmentDelinquencyChargeDetailResponse> delinquencyCharges = new ArrayList<>();
}
