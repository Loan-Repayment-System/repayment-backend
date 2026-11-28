package com.repayment.global.config;

import com.repayment.global.util.TransientDatabaseErrors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

/**
 * 집금 요청 저장(TX1)을 다시 시도하는 정책. 실패하면 집금 요청이 남지 않으므로 다시 해도 출금이 두 번 되지 않는다.
 * 일시적 DB 오류만 다시 하고, 업무 거절은 다시 해도 같은 결과라 바로 던진다.
 */
@Configuration
public class CollectionRequestRetryConfig {

    // 처음 시도를 포함한 최대 시도 횟수. 시도 사이에 1초, 2초를 기다린다.
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 1_000;
    private static final double BACKOFF_MULTIPLIER = 2;
    private static final long MAX_BACKOFF_MILLIS = 4_000;

    @Bean
    public RetryTemplate collectionRequestRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(MAX_ATTEMPTS)
                .exponentialBackoff(INITIAL_BACKOFF_MILLIS, BACKOFF_MULTIPLIER, MAX_BACKOFF_MILLIS)
                .retryOn(TransientDatabaseErrors::matches)
                .build();
    }
}
