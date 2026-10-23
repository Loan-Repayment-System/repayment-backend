package com.repayment.batch.listener;

import com.repayment.batch.step.StartClosingTasklet;
import com.repayment.domain.closing.service.DailyClosingService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 일마감 Job이 실패하면 기준일 일마감을 '실패'로 바꿔 같은 기준일로 다시 실행할 수 있게 한다.
 * startClosingStep에서 거절된 실행은 다른 실행이 진행 중인 행을 덮어쓰지 않도록 건드리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class DailyClosingJobListener implements JobExecutionListener {

    private final DailyClosingService dailyClosingService;

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.FAILED
                || !jobExecution.getExecutionContext().containsKey(StartClosingTasklet.CLOSING_STARTED)) {
            return;
        }
        dailyClosingService.fail(jobExecution.getJobParameters().getLocalDate("businessDate"));
    }
}
