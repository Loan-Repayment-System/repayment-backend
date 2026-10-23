package com.repayment.batch.writer;

import com.repayment.domain.loan.service.LoanStatusChangeService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 대출마다 오늘 판정에 따라 대출 상태를 갱신하고 연체금 쌓기·전환을 한다. process는 chunk 트랜잭션에 참여한다.
 */
@Component
@StepScope
public class StatusChangeWriter implements ItemWriter<Long> {

    private final LoanStatusChangeService loanStatusChangeService;
    private final LocalDate businessDate;

    public StatusChangeWriter(LoanStatusChangeService loanStatusChangeService,
                              @Value("#{jobParameters['businessDate']}") LocalDate businessDate) {
        this.loanStatusChangeService = loanStatusChangeService;
        this.businessDate = businessDate;
    }

    @Override
    public void write(Chunk<? extends Long> loanAccountIds) {
        for (Long loanAccountId : loanAccountIds) {
            loanStatusChangeService.process(loanAccountId, businessDate);
        }
    }
}
