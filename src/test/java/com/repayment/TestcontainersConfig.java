package com.repayment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;

/**
 * 통합 테스트용 MySQL 컨테이너. {@code @ServiceConnection}이 datasource 접속 정보를 덮어쓴다.
 * docker compose의 MySQL과 같은 버전·타임존을 쓴다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysql() {
        return new MySQLContainer<>("mysql:8.4")
                .withCommand("--default-time-zone=+09:00");
    }
}
