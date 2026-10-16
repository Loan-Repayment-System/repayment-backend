package com.repayment.domain.loan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상환할 때 잠그고 읽는 대출. memberId는 본인 확인에, repaymentAccountId는 이체 출금 계좌에 쓴다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentLoanAccountResponse {

    private Long loanAccountId;
    private Long memberId;
    private Long repaymentAccountId;
}
