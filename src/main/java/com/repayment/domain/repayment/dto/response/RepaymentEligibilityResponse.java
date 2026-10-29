package com.repayment.domain.repayment.dto.response;

import com.repayment.global.code.LoanJudgmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상환 가능 확인을 통과한 요청에서 상환 처리가 이어서 쓰는 값.
 * 상환 계좌는 출금 이체, latestJudgment는 충당 방식을 고르는 데,
 * repayableAmount는 충당이 위에서부터 채울 목록으로 쓴다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentEligibilityResponse {

    private String repaymentBankCode;
    private String repaymentAccountNo;
    private LoanJudgmentStatus latestJudgment;
    private RepayableAmountResponse repayableAmount;
}
