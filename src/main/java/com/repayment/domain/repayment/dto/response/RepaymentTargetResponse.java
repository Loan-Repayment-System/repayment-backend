package com.repayment.domain.repayment.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.repayment.global.code.RepaymentTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 갚을 금액을 채울 대상 하나와 그 남은 금액. repaymentOrder는 충당 순서로, 1부터 매긴다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentTargetResponse {

    private int repaymentOrder;
    private RepaymentTargetType type;
    // 충당이 채울 행을 찾는 데만 쓴다. 응답에는 내보내지 않는다.
    @JsonIgnore
    private Long targetId;
    private BigDecimal amount;
}
