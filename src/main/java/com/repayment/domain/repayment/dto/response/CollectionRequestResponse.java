package com.repayment.domain.repayment.dto.response;

import com.repayment.global.code.CollectionRequestStatus;
import com.repayment.global.code.RepaymentChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 집금 요청 1건. collectionRequestId는 INSERT할 때 DB가 만든 값으로 채워진다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionRequestResponse {

    private Long collectionRequestId;
    private Long loanAccountId;
    private RepaymentChannel repaymentChannelId;
    private CollectionRequestStatus collectionRequestStatusId;
    private LocalDate sendDate;
    private Integer telegramNo;
    private LocalDateTime sentAt;
    private String withdrawalBankCode;
    private String withdrawalAccountNo;
    private BigDecimal amount;
}
