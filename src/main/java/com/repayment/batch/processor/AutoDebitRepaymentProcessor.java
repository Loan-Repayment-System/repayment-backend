package com.repayment.batch.processor;

import com.repayment.domain.loan.mapper.LoanStatusJudgmentMapper;
import com.repayment.domain.repayment.dto.response.AutoDebitTargetResponse;
import com.repayment.domain.repayment.service.RepayableAmountService;
import com.repayment.global.code.LoanJudgmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 대출의 지금 갚을 금액을 계산한다. 0원이면 null을 돌려 Writer로 넘기지 않는다.
 * 정상·연체 대출은 납부 기한이 된 회차와 연체금만 더하므로, 납입일 전이면 0원이다.
 */
@Component
@RequiredArgsConstructor
public class AutoDebitRepaymentProcessor implements ItemProcessor<Long, AutoDebitTargetResponse> {

    private final LoanStatusJudgmentMapper loanStatusJudgmentMapper;
    private final RepayableAmountService repayableAmountService;

    @Override
    public AutoDebitTargetResponse process(Long loanAccountId) {
        // 판정 행이 없으면 0 정상으로 본다.
        LoanJudgmentStatus latestJudgment = loanStatusJudgmentMapper.findLatestJudgmentStatus(loanAccountId)
                .orElse(LoanJudgmentStatus.NORMAL);
        BigDecimal amount = repayableAmountService.calculate(loanAccountId, latestJudgment).getTotalAmount();
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return new AutoDebitTargetResponse(loanAccountId, amount);
    }
}
