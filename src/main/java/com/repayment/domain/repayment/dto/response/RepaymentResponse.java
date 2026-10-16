package com.repayment.domain.repayment.dto.response;

import com.repayment.global.code.RepaymentChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상환 1건의 기록. repaymentId는 INSERT할 때 DB가 만든 값으로 채워진다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentResponse {

    private Long repaymentId;
    private Long loanAccountId;
    private RepaymentChannel repaymentChannelId;
    private String requestKey;
    private BigDecimal paidAmount;
    private LocalDateTime paidAt;
    private String transactionNo;
}
