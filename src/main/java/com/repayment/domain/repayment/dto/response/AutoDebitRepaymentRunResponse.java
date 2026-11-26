package com.repayment.domain.repayment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 자동 이체 상환 수동 실행 결과. readCount는 읽은 대출 수, attemptedCount는 출금을 시도한 대출 수다.
 * 출금을 시도한 대출이 모두 상환된 것은 아니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoDebitRepaymentRunResponse {

    private LocalDate businessDate;
    private long readCount;
    private long attemptedCount;
}
