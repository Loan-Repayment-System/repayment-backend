package com.repayment.batch.writer;

import com.repayment.domain.schedule.service.RepaymentScheduleJudgmentService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 대출마다 끝나지 않은 회차를 기준일로 판정한다. judgeLoan은 chunk 트랜잭션에 참여한다.
 */
@Component
@StepScope
public class ScheduleJudgmentWriter implements ItemWriter<Long> {

    private final RepaymentScheduleJudgmentService repaymentScheduleJudgmentService;
    private final LocalDate businessDate;

    public ScheduleJudgmentWriter(RepaymentScheduleJudgmentService repaymentScheduleJudgmentService,
                                  @Value("#{jobParameters['businessDate']}") LocalDate businessDate) {
        this.repaymentScheduleJudgmentService = repaymentScheduleJudgmentService;
        this.businessDate = businessDate;
    }

    @Override
    public void write(Chunk<? extends Long> loanAccountIds) {
        for (Long loanAccountId : loanAccountIds) {
            repaymentScheduleJudgmentService.judgeLoan(loanAccountId, businessDate);
        }
    }
}
