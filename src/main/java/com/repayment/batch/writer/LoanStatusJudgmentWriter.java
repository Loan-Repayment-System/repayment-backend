package com.repayment.batch.writer;

import com.repayment.domain.loan.service.LoanStatusJudgmentService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 대출마다 기준일의 상태를 판정해 1행을 남긴다. judge는 chunk 트랜잭션에 참여한다.
 */
@Component
@StepScope
public class LoanStatusJudgmentWriter implements ItemWriter<Long> {

    private final LoanStatusJudgmentService loanStatusJudgmentService;
    private final LocalDate businessDate;

    public LoanStatusJudgmentWriter(LoanStatusJudgmentService loanStatusJudgmentService,
                                    @Value("#{jobParameters['businessDate']}") LocalDate businessDate) {
        this.loanStatusJudgmentService = loanStatusJudgmentService;
        this.businessDate = businessDate;
    }

    @Override
    public void write(Chunk<? extends Long> loanAccountIds) {
        for (Long loanAccountId : loanAccountIds) {
            loanStatusJudgmentService.judge(loanAccountId, businessDate);
        }
    }
}
