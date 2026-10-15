package com.repayment.domain.loan.service;

import com.repayment.domain.loan.dto.response.LoanStatusJudgmentTargetResponse;
import com.repayment.domain.loan.exception.LoanAccountNotFoundException;
import com.repayment.domain.loan.mapper.LoanStatusJudgmentMapper;
import com.repayment.global.code.LoanJudgmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 대출 하나를 기준일로 판정해 loan_status_judgment에 1행을 남긴다.
 * 어떤 대출을 넘길지, 그날 이미 판정했는지는 부르는 쪽이 정한다.
 */
@Service
@RequiredArgsConstructor
public class LoanStatusJudgmentService {

    private final LoanStatusJudgmentMapper loanStatusJudgmentMapper;
    private final LoanPayoffService loanPayoffService;
    private final LoanStatusJudge loanStatusJudge;

    @Transactional
    public void judge(Long loanAccountId, LocalDate businessDate) {
        LoanStatusJudgmentTargetResponse target = loanStatusJudgmentMapper.findJudgmentTarget(loanAccountId)
                .orElseThrow(() -> new LoanAccountNotFoundException(loanAccountId));
        boolean paidOff = loanPayoffService.isPaidOff(loanAccountId);
        LoanJudgmentStatus status = loanStatusJudge.judge(target, paidOff, businessDate);
        loanStatusJudgmentMapper.insertJudgment(loanAccountId, businessDate, status);
    }
}
