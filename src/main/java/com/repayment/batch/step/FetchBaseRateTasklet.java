package com.repayment.batch.step;

import com.repayment.domain.baserate.service.BaseRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 한국은행 API로 기준일 포함 최근 15일의 기준금리를 조회해 없는 공시일만 저장한다.
 * 실패하면 다시 시도하지 않고 그 예외로 Step이 실패한다.
 */
@Component
@RequiredArgsConstructor
public class FetchBaseRateTasklet implements Tasklet {

    private final BaseRateService baseRateService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate businessDate = contribution.getStepExecution().getJobParameters().getLocalDate("businessDate");
        baseRateService.collectKoribor12m(businessDate);
        return RepeatStatus.FINISHED;
    }
}
