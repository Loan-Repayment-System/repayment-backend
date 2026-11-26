package com.repayment.domain.repayment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 자동 이체 상환으로 출금할 대출과 금액. amount는 지금 갚을 금액 전액이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoDebitTargetResponse {

    private Long loanAccountId;
    private BigDecimal amount;
}
