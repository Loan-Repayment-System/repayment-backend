package com.repayment.batch.writer;

import com.repayment.domain.repayment.dto.response.AutoDebitTargetResponse;
import com.repayment.domain.repayment.service.RepaymentService;
import com.repayment.global.code.RepaymentChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * 대출마다 갚을 금액 전액을 자동 상환으로 상환한다. 상환은 집금 앞뒤로 트랜잭션을 스스로 나눈다.
 * 한 대출이 실패해도 나머지 대출은 계속 상환하도록, 예외는 로그만 남기고 넘어간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoDebitRepaymentWriter implements ItemWriter<AutoDebitTargetResponse> {

    private final RepaymentService repaymentService;

    @Override
    public void write(Chunk<? extends AutoDebitTargetResponse> targets) {
        for (AutoDebitTargetResponse target : targets) {
            try {
                repaymentService.repay(target.getLoanAccountId(), target.getAmount(), RepaymentChannel.AUTO);
            } catch (RuntimeException e) {
                log.warn("자동 이체 상환 실패 loanAccountId={} amount={}",
                        target.getLoanAccountId(), target.getAmount(), e);
            }
        }
    }
}
