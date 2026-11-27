package com.repayment.batch.scheduler;

import com.repayment.batch.launcher.AutoDebitRepaymentJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 매일 09:00·13:00·16:00(Asia/Seoul)에 그날을 기준일로 자동 이체 상환 Job을 실행한다.
 * 실패해도 다시 시도하지 않는다. 다음 시각에 다시 실행되고, 바로 다시 하려면 수동 실행으로 실행한다.
 * Job이 완료되지 않으면 관리자가 알아챌 수 있도록 에러 로그를 남긴다. 실행 ID로 Spring Batch 실행 기록을 찾는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoDebitRepaymentScheduler {

    private final AutoDebitRepaymentJobRunner autoDebitRepaymentJobRunner;

    @Scheduled(cron = "0 0 9,13,16 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            JobExecution execution = autoDebitRepaymentJobRunner.run();
            LocalDate businessDate = execution.getJobParameters().getLocalDate("businessDate");
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                log.info("자동 이체 상환 스케줄 실행 종료 businessDate={} status={}", businessDate, execution.getStatus());
                return;
            }
            List<Throwable> failures = execution.getAllFailureExceptions();
            log.error("자동 이체 상환 Job 미완료 businessDate={} status={} jobExecutionId={}",
                    businessDate, execution.getStatus(), execution.getId(), failures.isEmpty() ? null : failures.get(0));
        } catch (Exception e) {
            log.error("자동 이체 상환 스케줄 실행 실패", e);
        }
    }
}
