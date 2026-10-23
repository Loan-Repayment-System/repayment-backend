package com.repayment.batch.job;

import com.repayment.batch.listener.DailyClosingJobListener;
import com.repayment.batch.reader.LoanAccountIdReader;
import com.repayment.batch.step.CompleteClosingTasklet;
import com.repayment.batch.step.FetchBaseRateTasklet;
import com.repayment.batch.step.StartClosingTasklet;
import com.repayment.batch.writer.ScheduleJudgmentWriter;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 일마감 Job. 파라미터는 businessDate(기준일)와 requestedAt(실행마다 새 JobInstance가 되도록 넣는 요청 시각)이다.
 * Step 하나가 실패하면 뒤 Step은 실행하지 않고 Job이 실패로 끝난다.
 */
@Configuration
public class DailyClosingJobConfig {

    // chunk 크기 = 커밋 단위. Reader 페이지 크기도 같게 둔다.
    private static final int CHUNK_SIZE = 100;

    @Bean
    public Job dailyClosingJob(JobRepository jobRepository, Step startClosingStep, Step fetchBaseRateStep,
                               Step judgeScheduleStep, Step completeClosingStep,
                               DailyClosingJobListener dailyClosingJobListener) {
        return new JobBuilder("dailyClosingJob", jobRepository)
                .listener(dailyClosingJobListener)
                .start(startClosingStep)
                .next(fetchBaseRateStep)
                .next(judgeScheduleStep)
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

    // API를 기다리는 동안 DB 연결을 잡지 않도록 Step 트랜잭션을 쓰지 않는다. 저장은 도메인이 따로 트랜잭션으로 묶는다.
    @Bean
    public Step fetchBaseRateStep(JobRepository jobRepository, FetchBaseRateTasklet fetchBaseRateTasklet) {
        return new StepBuilder("fetchBaseRateStep", jobRepository)
                .tasklet(fetchBaseRateTasklet, new ResourcelessTransactionManager())
                .build();
    }

    @Bean
    public Step judgeScheduleStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                  LoanAccountIdReader scheduleJudgmentTargetReader,
                                  ScheduleJudgmentWriter scheduleJudgmentWriter) {
        return new StepBuilder("judgeScheduleStep", jobRepository)
                .<Long, Long>chunk(CHUNK_SIZE, transactionManager)
                .reader(scheduleJudgmentTargetReader)
                .writer(scheduleJudgmentWriter)
                .build();
    }

    // 가장 최근 판정이 완제가 아닌 대출
    @Bean
    @StepScope
    public LoanAccountIdReader scheduleJudgmentTargetReader(LoanAccountMapper loanAccountMapper) {
        return new LoanAccountIdReader(loanAccountMapper::findScheduleJudgmentTargetIds, CHUNK_SIZE);
    }

    @Bean
    public Step completeClosingStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                    CompleteClosingTasklet completeClosingTasklet) {
        return new StepBuilder("completeClosingStep", jobRepository)
                .tasklet(completeClosingTasklet, transactionManager)
                .build();
    }
}
