package com.repayment.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 매일 00:00(Asia/Seoul)에 그날을 기준일로 일마감 Job을 실행한다.
 * 실패해도 다시 시도하지 않는다. 원인을 고친 뒤 수동 실행으로 다시 실행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyClosingScheduler {

    private final JobLauncher jobLauncher;
    private final Job dailyClosingJob;
    private final Clock clock;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate businessDate = now.toLocalDate();
        JobParameters parameters = new JobParametersBuilder()
                .addLocalDate("businessDate", businessDate)
                .addLocalDateTime("requestedAt", now)
                .toJobParameters();
        try {
            JobExecution execution = jobLauncher.run(dailyClosingJob, parameters);
            log.info("일마감 스케줄 실행 종료 businessDate={} status={}", businessDate, execution.getStatus());
        } catch (Exception e) {
            log.error("일마감 스케줄 실행 실패 businessDate={}", businessDate, e);
        }
    }
}
