package com.repayment.batch.job;

import com.repayment.batch.processor.AutoDebitRepaymentProcessor;
import com.repayment.batch.writer.AutoDebitRepaymentWriter;
import com.repayment.domain.repayment.dto.response.AutoDebitTargetResponse;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 자동 이체 상환 Job. 파라미터는 businessDate(기준일)와 requestedAt(실행마다 새 JobInstance가 되도록 넣는 요청 시각)이다.
 */
@Configuration
public class AutoDebitRepaymentJobConfig {

    // chunk 크기 = 커밋 단위
    private static final int CHUNK_SIZE = 100;

    @Bean
    public Job autoDebitRepaymentJob(JobRepository jobRepository, Step autoDebitRepaymentStep) {
        return new JobBuilder("autoDebitRepaymentJob", jobRepository)
                .start(autoDebitRepaymentStep)
                .build();
    }

    // 상환이 집금 앞뒤로 트랜잭션을 나누므로, chunk 트랜잭션에 합쳐지지 않도록 Step 트랜잭션을 쓰지 않는다.
    @Bean
    public Step autoDebitRepaymentStep(JobRepository jobRepository,
                                       JdbcPagingItemReader<Long> autoDebitRepaymentTargetReader,
                                       AutoDebitRepaymentProcessor autoDebitRepaymentProcessor,
                                       AutoDebitRepaymentWriter autoDebitRepaymentWriter) {
        return new StepBuilder("autoDebitRepaymentStep", jobRepository)
                .<Long, AutoDebitTargetResponse>chunk(CHUNK_SIZE, new ResourcelessTransactionManager())
                .reader(autoDebitRepaymentTargetReader)
                .processor(autoDebitRepaymentProcessor)
                .writer(autoDebitRepaymentWriter)
                .build();
    }
}
