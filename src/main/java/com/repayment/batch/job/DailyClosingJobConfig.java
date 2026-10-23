package com.repayment.batch.job;

import com.repayment.batch.listener.DailyClosingJobListener;
import com.repayment.batch.step.CompleteClosingTasklet;
import com.repayment.batch.step.StartClosingTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 일마감 Job. 파라미터는 businessDate(기준일)와 requestedAt(실행마다 새 JobInstance가 되도록 넣는 요청 시각)이다.
 * Step 하나가 실패하면 뒤 Step은 실행하지 않고 Job이 실패로 끝난다.
 */
@Configuration
public class DailyClosingJobConfig {

    @Bean
    public Job dailyClosingJob(JobRepository jobRepository, Step startClosingStep, Step completeClosingStep,
                               DailyClosingJobListener dailyClosingJobListener) {
        return new JobBuilder("dailyClosingJob", jobRepository)
                .listener(dailyClosingJobListener)
                .start(startClosingStep)
                .next(completeClosingStep)
                .build();
    }

    @Bean
    public Step startClosingStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                 StartClosingTasklet startClosingTasklet) {
        return new StepBuilder("startClosingStep", jobRepository)
                .tasklet(startClosingTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step completeClosingStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                    CompleteClosingTasklet completeClosingTasklet) {
        return new StepBuilder("completeClosingStep", jobRepository)
                .tasklet(completeClosingTasklet, transactionManager)
                .build();
    }
}
