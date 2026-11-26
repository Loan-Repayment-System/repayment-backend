package com.repayment.batch.launcher;

import com.repayment.domain.repayment.dto.response.AutoDebitRepaymentRunResponse;
import com.repayment.domain.repayment.exception.AutoDebitRepaymentFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 오늘(Asia/Seoul)을 기준일로 자동 이체 상환 Job을 실행한다. 스케줄 실행과 수동 실행이 같은 Job 파라미터로 실행하도록 한곳에 둔다.
 * Job은 끝날 때까지 기다린다. Step이 실패해도 JobLauncher는 예외 대신 '실패' 상태의 실행 결과를 돌려준다.
 */
@Component
@RequiredArgsConstructor
public class AutoDebitRepaymentJobRunner {

    private final JobLauncher jobLauncher;
    private final Job autoDebitRepaymentJob;
    private final Clock clock;

    /**
     * 스케줄 실행용. 실행 결과를 그대로 돌려준다.
     */
    public JobExecution run() throws JobExecutionException {
        return launch(LocalDateTime.now(clock));
    }

    /**
     * 수동 실행용. 완료되지 않았으면 실패 원인을 담은 예외를 던져 실패 응답이 되게 한다.
     */
    public AutoDebitRepaymentRunResponse runManually() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate businessDate = now.toLocalDate();
        JobExecution execution;
        try {
            execution = launch(now);
        } catch (JobExecutionException e) {
            throw new AutoDebitRepaymentFailedException(businessDate, e);
        }
        if (execution.getStatus() != BatchStatus.COMPLETED) {
            List<Throwable> failures = execution.getAllFailureExceptions();
            throw new AutoDebitRepaymentFailedException(businessDate, failures.isEmpty() ? null : failures.get(0));
        }
        return AutoDebitRepaymentRunResponse.builder()
                .businessDate(businessDate)
                .readCount(execution.getStepExecutions().stream().mapToLong(StepExecution::getReadCount).sum())
                .attemptedCount(execution.getStepExecutions().stream().mapToLong(StepExecution::getWriteCount).sum())
                .build();
    }

    private JobExecution launch(LocalDateTime now) throws JobExecutionException {
        JobParameters parameters = new JobParametersBuilder()
                .addLocalDate("businessDate", now.toLocalDate())
                .addLocalDateTime("requestedAt", now)
                .toJobParameters();
        return jobLauncher.run(autoDebitRepaymentJob, parameters);
    }
}
