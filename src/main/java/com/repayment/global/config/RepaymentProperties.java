package com.repayment.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 상환 설정. collectionAccountNo는 상환 금액이 입금되는 은행 수납 계좌의 계좌번호다.
 */
@ConfigurationProperties("repayment")
public record RepaymentProperties(String collectionAccountNo) {
}
