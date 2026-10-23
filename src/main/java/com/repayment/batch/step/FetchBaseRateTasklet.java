package com.repayment.batch.step;

import com.repayment.domain.baserate.service.BaseRateService;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.backoff.ThreadWaitSleeper;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.Map;

/**
 * 한국은행 API로 기준일 포함 최근 15일의 기준금리를 조회해 없는 공시일만 저장한다.
 * 타임아웃·연결 실패와 5xx만 1초 → 2초 → 4초 간격으로 3번 더 시도하고, 그래도 실패하면 마지막 예외를 그대로 던진다.
 * 저장은 없는 공시일만 넣으므로 조회와 저장을 통째로 다시 시도해도 된다.
 */
@Component
public class FetchBaseRateTasklet implements Tasklet {

    // 첫 시도 + 재시도 3번
    private static final int MAX_ATTEMPTS = 4;
    private static final long INITIAL_INTERVAL_MILLIS = 1_000;
    private static final double MULTIPLIER = 2;
    private static final long MAX_INTERVAL_MILLIS = 4_000;

    private final BaseRateService baseRateService;
    private final RetryTemplate retryTemplate;

    @Autowired
    public FetchBaseRateTasklet(BaseRateService baseRateService) {
        this(baseRateService, new ThreadWaitSleeper());
    }

    // 테스트에서 실제로 기다리지 않고 간격만 기록하도록 Sleeper를 바꿔 넣는다.
    FetchBaseRateTasklet(BaseRateService baseRateService, Sleeper sleeper) {
        this.baseRateService = baseRateService;
        this.retryTemplate = createRetryTemplate(sleeper);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate businessDate = contribution.getStepExecution().getJobParameters().getLocalDate("businessDate");
        retryTemplate.execute(context -> baseRateService.collectKoribor12m(businessDate));
        return RepeatStatus.FINISHED;
    }

    private static RetryTemplate createRetryTemplate(Sleeper sleeper) {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(MAX_ATTEMPTS, Map.of(
                ResourceAccessException.class, true,
                HttpServerErrorException.class, true));

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(INITIAL_INTERVAL_MILLIS);
        backOffPolicy.setMultiplier(MULTIPLIER);
        backOffPolicy.setMaxInterval(MAX_INTERVAL_MILLIS);
        backOffPolicy.setSleeper(sleeper);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        return retryTemplate;
    }
}
