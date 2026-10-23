package com.repayment.batch.step;

import com.repayment.domain.closing.service.DailyClosingService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 기준일 일마감을 '진행중'으로 바꾼다. 성공하면 JobExecutionContext에 closingStarted를 남겨,
 * afterJob이 이 실행이 바꾼 행만 '실패'로 되돌리게 한다.
 */
@Component
@RequiredArgsConstructor
public class StartClosingTasklet implements Tasklet {

    public static final String CLOSING_STARTED = "closingStarted";

    private final DailyClosingService dailyClosingService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate businessDate = contribution.getStepExecution().getJobParameters().getLocalDate("businessDate");
        dailyClosingService.start(businessDate);
        contribution.getStepExecution().getJobExecution().getExecutionContext().put(CLOSING_STARTED, true);
        return RepeatStatus.FINISHED;
    }
}
