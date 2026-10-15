package com.repayment.domain.loan.dto.response;

import com.repayment.global.code.LoanJudgmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 대출 상태 판정 1행.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanStatusJudgmentResponse {

    private LocalDate businessDate;
    private LoanJudgmentStatus loanJudgmentStatusId;
}
