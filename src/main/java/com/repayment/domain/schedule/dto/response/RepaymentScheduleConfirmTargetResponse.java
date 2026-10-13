package com.repayment.domain.schedule.dto.response;

import com.repayment.global.code.InterestType;
import com.repayment.global.code.RepaymentMethod;
import com.repayment.global.code.RepaymentScheduleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 회차 하나를 확정하는 데 필요한 값. 금리 단위는 %다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentScheduleConfirmTargetResponse {

    private Long repaymentScheduleId;
    private Long loanAccountId;
    private Integer installmentNo;
    private RepaymentScheduleStatus repaymentScheduleStatusId;
    private LocalDate scheduledDate;
    private InterestType interestTypeId;
    private RepaymentMethod repaymentMethodId;
    private BigDecimal contractBaseRate;
    private BigDecimal contractAdditionalRate;
    private BigDecimal contractPrincipal;
    private Integer totalInstallments;
    private Integer lastConfirmedInstallmentNo;
    // 이 회차보다 앞선 회차들의 scheduled_principal 합
    private BigDecimal priorScheduledPrincipalSum;
}
