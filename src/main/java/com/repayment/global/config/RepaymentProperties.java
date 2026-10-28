package com.repayment.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 상환 설정. collection은 상환 금액이 입금되는 수납 계좌,
 * disbursement는 대출금을 지급하는 지급 계좌의 은행코드와 계좌번호다.
 */
@ConfigurationProperties("repayment")
public record RepaymentProperties(String collectionBankCode, String collectionAccountNo,
                                  String disbursementBankCode, String disbursementAccountNo) {
}
