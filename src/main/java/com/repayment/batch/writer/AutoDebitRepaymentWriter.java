package com.repayment.batch.writer;

import com.repayment.domain.repayment.dto.response.AutoDebitTargetResponse;
import com.repayment.domain.repayment.exception.AutoDebitRepaymentAbortedException;
import com.repayment.domain.repayment.exception.RepaymentExceedsRepayableAmountException;
import com.repayment.domain.repayment.service.RepaymentService;
import com.repayment.global.code.RepaymentChannel;
import com.repayment.global.util.TransientDatabaseErrors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 대출마다 갚을 금액 전액을 자동 상환으로 상환한다. 상환은 집금 앞뒤로 트랜잭션을 스스로 나눈다.
 * 한 대출이 실패해도 나머지 대출은 계속 상환한다. DB 장애일 때만 예외를 던져 Job을 멈춘다.
 *
 * <ul>
 *   <li>갚을 금액 초과: Processor 계산 뒤 고객이 일부를 먼저 갚았다. 남은 갚을 금액으로 한 번 더 상환한다.</li>
 *   <li>잠금 획득 실패를 뺀 일시적 DB 오류: 상환 모듈이 다시 시도하고도 실패했으므로 Job을 멈춘다.</li>
 *   <li>그 밖: 경고 로그를 남기고 다음 대출로 넘어간다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoDebitRepaymentWriter implements ItemWriter<AutoDebitTargetResponse> {

    private final RepaymentService repaymentService;

    @Override
    public void write(Chunk<? extends AutoDebitTargetResponse> targets) {
        for (AutoDebitTargetResponse target : targets) {
            repay(target.getLoanAccountId(), target.getAmount());
        }
    }

    private void repay(Long loanAccountId, BigDecimal amount) {
        try {
            repaymentService.repay(loanAccountId, amount, RepaymentChannel.AUTO);
        } catch (RepaymentExceedsRepayableAmountException e) {
            log.info("자동 이체 상환 금액을 남은 갚을 금액으로 다시 상환 loanAccountId={} amount={} repayableAmount={}",
                    loanAccountId, amount, e.getRepayableAmount());
            repayRemaining(loanAccountId, e.getRepayableAmount());
        } catch (RuntimeException e) {
            handleFailure(loanAccountId, amount, e);
        }
    }

    // 다시 초과로 거절되어도 더 하지 않는다. 남은 금액은 다음 실행에서 상환한다.
    private void repayRemaining(Long loanAccountId, BigDecimal amount) {
        try {
            repaymentService.repay(loanAccountId, amount, RepaymentChannel.AUTO);
        } catch (RuntimeException e) {
            handleFailure(loanAccountId, amount, e);
        }
    }

    private void handleFailure(Long loanAccountId, BigDecimal amount, RuntimeException e) {
        if (isDatabaseFailure(e)) {
            throw new AutoDebitRepaymentAbortedException(loanAccountId, e);
        }
        log.warn("자동 이체 상환 실패 loanAccountId={} amount={}", loanAccountId, amount, e);
    }

    // 잠금 획득 실패는 그 대출만의 문제라 Job을 멈추지 않는다. 일시적 DB 오류에 들어가므로 따로 뺀다.
    private static boolean isDatabaseFailure(RuntimeException e) {
        return TransientDatabaseErrors.matches(e) && !(e instanceof PessimisticLockingFailureException);
    }
}
