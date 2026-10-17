package com.repayment.domain.loan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 대출 계약 1건. loanContractId는 INSERT할 때 DB가 만든 값으로 채워진다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanContractResponse {

    private Long loanContractId;
    private Long loanProductId;
    private Long memberId;
    private LocalDate contractDate;
    private LocalDate maturityDate;
    private BigDecimal contractPrincipal;
    private BigDecimal contractAdditionalRate;
    private BigDecimal contractBaseRate;
    private Integer totalInstallments;
}
