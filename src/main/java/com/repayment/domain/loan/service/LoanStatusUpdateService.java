package com.repayment.domain.loan.service;

import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.global.code.LoanJudgmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객에게 보여 주는 대출 상태(loan_status_id)를 오늘 판정에 맞춘다.
 * 오늘 판정은 부르는 쪽이 넘긴다. 판정에는 쓰지 않는 값이다.
 */
@Service
@RequiredArgsConstructor
public class LoanStatusUpdateService {

    private final LoanAccountMapper loanAccountMapper;

    @Transactional
    public void update(Long loanAccountId, LoanJudgmentStatus todayJudgment) {
        loanAccountMapper.updateLoanStatus(loanAccountId, todayJudgment.getLoanStatus());
    }
}
