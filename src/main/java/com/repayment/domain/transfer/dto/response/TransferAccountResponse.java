package com.repayment.domain.transfer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 이체할 때 잠그고 읽은 계좌. 계좌 상태는 보지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferAccountResponse {

    private Long accountId;
    private String accountNo;
    private BigDecimal balance;
}
