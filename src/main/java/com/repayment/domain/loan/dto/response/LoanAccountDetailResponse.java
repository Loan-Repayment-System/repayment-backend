package com.repayment.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.repayment.domain.acceleration.dto.response.LoanAccelerationDetailResponse;
import com.repayment.domain.maturity.dto.response.LoanMaturityOverdueDetailResponse;
import com.repayment.domain.repayment.dto.response.RepayableAmountResponse;
import com.repayment.domain.schedule.dto.response.InstallmentDetailResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 대출 하나의 현재 모습. loan·status는 항상 들어가고, 나머지는 대출 상태에 맞는 것만 들어간다.
 * 들어가지 않는 묶음은 응답에서 뺀다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class LoanAccountDetailResponse {

    private LoanBasicInfoResponse loan;
    private LoanCurrentStatusResponse status;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<InstallmentDetailResponse> installments;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LoanAccelerationDetailResponse acceleration;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LoanMaturityOverdueDetailResponse maturityOverdue;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private RepayableAmountResponse repayableAmount;
}
