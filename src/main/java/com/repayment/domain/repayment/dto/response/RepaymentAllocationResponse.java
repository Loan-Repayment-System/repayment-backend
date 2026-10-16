package com.repayment.domain.repayment.dto.response;

import com.repayment.global.code.RepaymentTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 상환 1건이 대상 하나에 충당한 금액. paidAmount는 이 상환의 금액이라 누적이 아니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentAllocationResponse {

    private Long repaymentId;
    private RepaymentTargetType repaymentTargetTypeId;
    private Long targetId;
    private BigDecimal paidAmount;
}
