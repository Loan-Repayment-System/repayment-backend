package com.repayment.domain.loan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상환할 때 잠그고 읽는 대출. 상환 계좌는 출금 이체에 쓴다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentLoanAccountResponse {

    private Long loanAccountId;
    private String repaymentBankCode;
    private String repaymentAccountNo;
}
