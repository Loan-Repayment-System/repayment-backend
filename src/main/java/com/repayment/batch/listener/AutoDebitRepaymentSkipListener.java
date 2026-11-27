package com.repayment.batch.listener;

import com.repayment.domain.repayment.dto.response.AutoDebitTargetResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;

/**
 * 자동 이체 상환에서 skip한 대출을 로그로 남긴다. skip이 있어도 Job은 완료되므로, 이 로그로만 드러난다.
 */
@Slf4j
@Component
public class AutoDebitRepaymentSkipListener implements SkipListener<Long, AutoDebitTargetResponse> {

    @Override
    public void onSkipInProcess(Long loanAccountId, Throwable t) {
        log.warn("자동 이체 상환 대상에서 skip loanAccountId={}", loanAccountId, t);
    }
}
