package com.repayment.batch.job;

import com.repayment.batch.listener.AutoDebitRepaymentSkipListener;
import com.repayment.batch.processor.AutoDebitRepaymentProcessor;
import com.repayment.batch.writer.AutoDebitRepaymentWriter;
import com.repayment.domain.repayment.dto.response.AutoDebitTargetResponse;
import com.repayment.global.exception.DataInconsistencyException;
import com.repayment.global.util.TransientDatabaseErrors;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.FaultTolerantStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;

/**
 * 자동 이체 상환 Job. 파라미터는 businessDate(기준일)와 requestedAt(실행마다 새 JobInstance가 되도록 넣는 요청 시각)이다.
 */
@Configuration
public class AutoDebitRepaymentJobConfig {

    // chunk 크기 = 커밋 단위
    private static final int CHUNK_SIZE = 100;

    // 처음 시도를 포함한 대출 한 건의 최대 계산 횟수. 시도 사이에 1초, 2초를 기다린다.
    private static final int MAX_PROCESS_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 1_000;
    private static final double BACKOFF_MULTIPLIER = 2;
    private static final long MAX_BACKOFF_MILLIS = 4_000;

    // 한 번 실행에서 skip할 수 있는 대출 수. 넘으면 데이터가 크게 잘못된 것으로 보고 Job을 실패시킨다.
    private static final int SKIP_LIMIT = 100;

    @Bean
    public Job autoDebitRepaymentJob(JobRepository jobRepository, Step autoDebitRepaymentStep) {
        return new JobBuilder("autoDebitRepaymentJob", jobRepository)
                .start(autoDebitRepaymentStep)
                .build();
    }

    // 상환이 집금 앞뒤로 트랜잭션을 나누므로, chunk 트랜잭션에 합쳐지지 않도록 Step 트랜잭션을 쓰지 않는다.
    // DB 일시 오류는 다시 계산하고, 소진하면 Job을 실패시킨다. 대출 데이터 불일치는 그 대출만 skip한다.
    // Writer가 retry·skip 대상 예외를 던지면 chunk를 다시 써 이중 출금하므로, Writer는 그런 예외를 밖으로 던지지 않는다.
    // DB 장애나 펌뱅킹 장애일 때만 어느 쪽에도 들지 않는 예외로 감싸 던져, chunk를 다시 쓰지 않고 Step을 멈춘다.
    @Bean
    public Step autoDebitRepaymentStep(JobRepository jobRepository,
                                       JdbcPagingItemReader<Long> autoDebitRepaymentTargetReader,
                                       AutoDebitRepaymentProcessor autoDebitRepaymentProcessor,
                                       AutoDebitRepaymentWriter autoDebitRepaymentWriter,
                                       AutoDebitRepaymentSkipListener autoDebitRepaymentSkipListener) {
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(INITIAL_BACKOFF_MILLIS);
        backOffPolicy.setMultiplier(BACKOFF_MULTIPLIER);
        backOffPolicy.setMaxInterval(MAX_BACKOFF_MILLIS);

        FaultTolerantStepBuilder<Long, AutoDebitTargetResponse> builder =
                new StepBuilder("autoDebitRepaymentStep", jobRepository)
                        .<Long, AutoDebitTargetResponse>chunk(CHUNK_SIZE, new ResourcelessTransactionManager())
                        .reader(autoDebitRepaymentTargetReader)
                        .processor(autoDebitRepaymentProcessor)
                        .writer(autoDebitRepaymentWriter)
                        .faultTolerant();
        TransientDatabaseErrors.TYPES.forEach(builder::retry);
        return builder
                .retryLimit(MAX_PROCESS_ATTEMPTS)
                .backOffPolicy(backOffPolicy)
                .skip(DataInconsistencyException.class)
                .skipLimit(SKIP_LIMIT)
                .listener(autoDebitRepaymentSkipListener)
                .build();
    }
}
