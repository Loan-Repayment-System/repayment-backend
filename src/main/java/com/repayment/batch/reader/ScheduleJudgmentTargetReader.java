package com.repayment.batch.reader;

import com.repayment.domain.loan.mapper.LoanAccountMapper;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 스케줄 판정 대상: 가장 최근 판정이 완제가 아닌 대출. 판정이 없는 새 대출도 포함한다.
 * 마지막으로 읽은 ID를 들고 있으므로 Step을 실행할 때마다 새로 만든다(@StepScope).
 */
@Component
@StepScope
public class ScheduleJudgmentTargetReader extends LoanAccountIdPagingReader {

    private static final int PAGE_SIZE = 100;

    private final LoanAccountMapper loanAccountMapper;

    public ScheduleJudgmentTargetReader(LoanAccountMapper loanAccountMapper) {
        super(PAGE_SIZE);
        this.loanAccountMapper = loanAccountMapper;
    }

    @Override
    protected List<Long> findPage(long afterLoanAccountId, int limit) {
        return loanAccountMapper.findScheduleJudgmentTargetIds(afterLoanAccountId, limit);
    }
}
