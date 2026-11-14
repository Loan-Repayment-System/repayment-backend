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
import java.time.format.DateTimeFormatter;

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
    private Integer retryCount;
    private LocalDateTime nextRetryAt;

    /**
     * 펌뱅킹이 같은 거래로 보는 키(전송일자, 전문번호)를 이어 붙인다. 예) 20261016000001
     */
    public String getTransactionNo() {
        return sendDate.format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("%06d", telegramNo);
    }
}
