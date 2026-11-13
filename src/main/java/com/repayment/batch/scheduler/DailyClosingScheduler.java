package com.repayment.batch.scheduler;

import com.repayment.batch.launcher.DailyClosingJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 00:00(Asia/Seoul)에 그날을 기준일로 일마감 Job을 실행한다.
 * 실패해도 다시 시도하지 않는다. 원인을 고친 뒤 수동 실행으로 다시 실행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyClosingScheduler {

    private final DailyClosingJobRunner dailyClosingJobRunner;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            JobExecution execution = dailyClosingJobRunner.run();
            log.info("일마감 스케줄 실행 종료 businessDate={} status={}",
                    execution.getJobParameters().getLocalDate("businessDate"), execution.getStatus());
        } catch (Exception e) {
            log.error("일마감 스케줄 실행 실패", e);
        }
    }
}
