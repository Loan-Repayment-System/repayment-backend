package com.repayment.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 펌뱅킹 전문 서버 설정. companyNo는 펌뱅킹에 등록된 캐피탈의 업체번호다.
 * connectTimeout은 연결을 맺기까지, readTimeout은 응답을 기다리는 시간이다.
 * retryDelay는 응답을 받지 못한 집금을 다시 보내기 전에 기다리는 기본 시간이다.
 */
@ConfigurationProperties("firmbanking")
public record FirmbankingProperties(String host, int port, String companyNo,
                                    Duration connectTimeout, Duration readTimeout,
                                    Duration retryDelay) {
}
