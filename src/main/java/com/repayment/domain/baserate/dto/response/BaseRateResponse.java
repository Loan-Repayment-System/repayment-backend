package com.repayment.domain.baserate.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 공시된 기준금리 하나. rate의 단위는 %다 (base_rate_history.rate와 같음).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaseRateResponse {

    private LocalDate announcedDate;
    private BigDecimal rate;
}
