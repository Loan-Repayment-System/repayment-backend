package com.repayment.domain.transfer.dto.response;

import com.repayment.global.code.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 이체 1건으로 한 계좌에 남길 거래 기록. 이체 1건에 출금·입금 2개가 같은 거래번호로 생긴다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountTransactionResponse {

    private Long accountId;
    private TransactionType transactionTypeId;
    private String transactionNo;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String counterpartAccountNo;
    private LocalDateTime transactedAt;
}
