package com.repayment.domain.repayment.service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 시각을 2026-10-17 10:00:00으로 고정한다.
 * 테스트 클래스 안에 두면 @Nested 클래스와 컨텍스트가 갈라지므로 따로 둔다.
 */
@TestConfiguration(proxyBeanMethods = false)
class FixedClockConfig {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 10, 0, 0);

    @Bean
    @Primary
    Clock fixedClock() {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        return Clock.fixed(NOW.atZone(zone).toInstant(), zone);
    }
}
