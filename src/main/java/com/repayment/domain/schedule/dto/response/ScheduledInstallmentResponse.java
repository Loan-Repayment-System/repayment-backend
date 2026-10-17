package com.repayment.domain.schedule.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 개설할 때 만드는 회차 1개. 금액·금리는 예정일 5일 전에 확정하므로 회차 번호와 예정일만 가진다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledInstallmentResponse {

    private Integer installmentNo;
    private LocalDate scheduledDate;
}
