package com.repayment.domain.repayment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상환 결과가 아직 확정되지 않았을 때 실패 응답의 data에 담는다. 거래번호로 나중에 결과를 찾는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentPendingResponse {

    private String transactionNo;
}
