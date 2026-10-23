package com.repayment.batch.step;

import com.repayment.domain.closing.service.DailyClosingService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * 기준일 일마감을 '진행중'에서 '완료'로 바꾼다. 이때부터 그날 상환을 받는다.
 */
@Component
@RequiredArgsConstructor
public class CompleteClosingTasklet implements Tasklet {

    private final DailyClosingService dailyClosingService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        dailyClosingService.complete(contribution.getStepExecution().getJobParameters().getLocalDate("businessDate"));
        return RepeatStatus.FINISHED;
    }
}
