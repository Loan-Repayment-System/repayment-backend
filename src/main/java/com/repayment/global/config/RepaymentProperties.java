package com.repayment.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 상환 설정. collectionAccountNo는 상환 금액이 입금되는 은행 수납 계좌,
 * disbursementAccountNo는 대출금을 지급하는 은행 지급 계좌의 계좌번호다.
 */
@ConfigurationProperties("repayment")
public record RepaymentProperties(String collectionAccountNo, String disbursementAccountNo) {
}
