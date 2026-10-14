package com.repayment.domain.loan.dto.response;

import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.RepaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 대출 하나의 상태를 판정하는 데 필요한 값. 밀린 회차는 상태가 '연체'인 회차다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanStatusJudgmentTargetResponse {

    private Long loanAccountId;
    // 그 대출의 가장 최근 판정. 판정 행이 없으면 0 정상
    private LoanJudgmentStatus previousJudgmentStatus;
    private RepaymentMethod repaymentMethodId;
    private BigDecimal contractPrincipal;
    private LocalDate maturityDate;
    private int overdueInstallmentCount;
    // 밀린 회차가 없으면 null
    private LocalDate oldestOverdueDate;
}
