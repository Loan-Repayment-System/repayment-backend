package com.repayment.domain.loan.dto.response;

import com.repayment.global.code.LoanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 개설한 대출 계좌 1건. loanAccountId는 INSERT할 때 DB가 만든 값으로 채워진다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanAccountResponse {

    private Long loanAccountId;
    private Long loanContractId;
    private Long repaymentAccountId;
    private String accountNo;
    private LoanStatus loanStatusId;
    private BigDecimal loanPrincipalBalance;
    private Integer monthlyPaymentDay;
}
