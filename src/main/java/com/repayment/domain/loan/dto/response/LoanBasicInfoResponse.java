package com.repayment.domain.loan.dto.response;

import com.repayment.global.code.InterestType;
import com.repayment.global.code.RepaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 대출 현황 조회의 대출 기본 정보. 계약·상품·회원에서 가져온다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanBasicInfoResponse {

    private Long loanAccountId;
    private String memberName;
    private String productName;
    private InterestType interestType;
    private RepaymentMethod repaymentMethod;
    private LocalDate contractDate;
    private LocalDate maturityDate;
    private BigDecimal contractPrincipal;
    private BigDecimal loanPrincipalBalance;
    private BigDecimal contractBaseRate;
    private BigDecimal contractAdditionalRate;
    private Integer totalInstallments;
}
