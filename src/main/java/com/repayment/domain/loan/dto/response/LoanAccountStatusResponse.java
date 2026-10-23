package com.repayment.domain.loan.dto.response;

import com.repayment.global.code.LoanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 대출의 지금 원금잔액과 상태. 상환을 마친 뒤 결과로 돌려줄 때 쓴다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanAccountStatusResponse {

    private BigDecimal loanPrincipalBalance;
    private LoanStatus loanStatusId;
}
