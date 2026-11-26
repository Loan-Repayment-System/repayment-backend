package com.repayment.batch.scheduler;

import com.repayment.batch.launcher.AutoDebitRepaymentJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 09:00·13:00·16:00(Asia/Seoul)에 그날을 기준일로 자동 이체 상환 Job을 실행한다.
 * 실패해도 다시 시도하지 않는다. 다음 시각에 다시 실행되고, 바로 다시 하려면 수동 실행으로 실행한다.
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
            log.info("자동 이체 상환 스케줄 실행 종료 businessDate={} status={}",
                    execution.getJobParameters().getLocalDate("businessDate"), execution.getStatus());
        } catch (Exception e) {
            log.error("자동 이체 상환 스케줄 실행 실패", e);
        }
    }
}
