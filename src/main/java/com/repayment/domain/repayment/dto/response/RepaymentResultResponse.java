package com.repayment.domain.repayment.dto.response;

import com.repayment.global.code.LoanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상환 1건의 결과. 원금잔액·대출 상태·남은 갚을 금액은 상환을 모두 반영한 뒤의 값이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentResultResponse {

    private BigDecimal paidAmount;
    private LocalDateTime paidAt;
    private BigDecimal loanPrincipalBalance;
    private LoanStatus loanStatus;
    private BigDecimal remainingRepayableAmount;
}
