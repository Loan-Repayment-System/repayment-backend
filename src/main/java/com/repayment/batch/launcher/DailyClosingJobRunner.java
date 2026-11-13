package com.repayment.batch.launcher;

import com.repayment.domain.closing.dto.response.DailyClosingRunResponse;
import com.repayment.domain.closing.exception.DailyClosingFailedException;
import com.repayment.global.code.DailyClosingStatus;
import com.repayment.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 오늘(Asia/Seoul)을 기준일로 일마감 Job을 실행한다. 스케줄 실행과 수동 실행이 같은 Job 파라미터로 실행하도록 한곳에 둔다.
 * Job은 끝날 때까지 기다린다. Step이 실패해도 JobLauncher는 예외 대신 '실패' 상태의 실행 결과를 돌려준다.
 */
@Component
@RequiredArgsConstructor
public class DailyClosingJobRunner {

    private final JobLauncher jobLauncher;
    private final Job dailyClosingJob;
    private final Clock clock;

    /**
     * 스케줄 실행용. 실행 결과를 그대로 돌려준다.
     */
    public JobExecution run() throws JobExecutionException {
        return launch(LocalDateTime.now(clock));
    }

    /**
     * 수동 실행용. 완료되지 않았으면 실패 원인을 예외로 던져 실패 응답이 되게 한다.
     */
    public DailyClosingRunResponse runManually() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate businessDate = now.toLocalDate();
        JobExecution execution;
        try {
            execution = launch(now);
        } catch (JobExecutionException e) {
            throw new DailyClosingFailedException(businessDate, e);
        }
        if (execution.getStatus() != BatchStatus.COMPLETED) {
            throw failureCause(execution, businessDate);
        }
        return DailyClosingRunResponse.builder()
                .businessDate(businessDate)
                .status(DailyClosingStatus.COMPLETED)
                .build();
    }

    private JobExecution launch(LocalDateTime now) throws JobExecutionException {
        JobParameters parameters = new JobParametersBuilder()
                .addLocalDate("businessDate", now.toLocalDate())
                .addLocalDateTime("requestedAt", now)
                .toJobParameters();
        return jobLauncher.run(dailyClosingJob, parameters);
    }

    // 업무 예외(이미 완료된 일마감 등)는 그 에러 코드가 응답에 드러나도록 그대로 쓰고, 그 밖의 원인은 일마감 실패로 감싼다.
    private BusinessException failureCause(JobExecution execution, LocalDate businessDate) {
        List<Throwable> failures = execution.getAllFailureExceptions();
        return failures.stream()
                .filter(BusinessException.class::isInstance)
                .map(BusinessException.class::cast)
                .findFirst()
                .orElseGet(() -> new DailyClosingFailedException(businessDate,
                        failures.isEmpty() ? null : failures.get(0)));
    }
}
