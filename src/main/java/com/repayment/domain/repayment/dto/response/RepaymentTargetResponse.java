package com.repayment.domain.repayment.dto.response;

import com.repayment.domain.repayment.service.RepaymentTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 갚을 금액을 채울 대상 하나와 그 남은 금액.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentTargetResponse {

    private RepaymentTargetType type;
    private Long targetId;
    private BigDecimal amount;
}
