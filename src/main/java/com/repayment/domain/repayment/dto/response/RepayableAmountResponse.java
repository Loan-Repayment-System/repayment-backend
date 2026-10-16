package com.repayment.domain.repayment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 지금 갚을 수 있는 금액. targets는 충당 순서(repaymentOrder)로 정렬한 대상이고 totalAmount는 그 합이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepayableAmountResponse {

    private BigDecimal totalAmount;
    private List<RepaymentTargetResponse> targets;
}
