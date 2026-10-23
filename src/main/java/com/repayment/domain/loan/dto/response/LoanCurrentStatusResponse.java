package com.repayment.domain.loan.dto.response;

import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.LoanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대출 현황 조회의 현재 상태. 판정 기록이 없으면 latestJudgment는 null이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanCurrentStatusResponse {

    private LoanStatus loanStatus;
    private LoanJudgmentStatus latestJudgment;
}
