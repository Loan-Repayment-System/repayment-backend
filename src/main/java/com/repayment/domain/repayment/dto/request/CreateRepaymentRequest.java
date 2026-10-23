package com.repayment.domain.repayment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 상환 요청. 금액 형식(1원 이상 원 단위)은 상환 가능 확인이 검사한다.
 */
@Getter
@NoArgsConstructor
public class CreateRepaymentRequest {

    @NotNull
    private BigDecimal amount;
}
