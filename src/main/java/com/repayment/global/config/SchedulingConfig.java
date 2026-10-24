package com.repayment.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄 실행을 켠다. 통합 테스트 중에 일마감이 끼어들지 않도록 test 프로필에서는 끈다.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
