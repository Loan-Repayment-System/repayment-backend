package com.repayment.domain.closing.dto.response;

import com.repayment.global.code.DailyClosingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 일마감 수동 실행 결과.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyClosingRunResponse {

    private LocalDate businessDate;
    private DailyClosingStatus status;
}
