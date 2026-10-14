package com.repayment.domain.loan.service;

import com.repayment.domain.loan.exception.LoanAccountNotFoundException;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 대출을 다 갚았는지 확인한다. 원금잔액과 이자·연체금 미납이 모두 0이어야 다 갚은 것이다.
 */
@Service
@RequiredArgsConstructor
public class LoanPayoffService {

    private final LoanAccountMapper loanAccountMapper;

    public boolean isPaidOff(Long loanAccountId) {
        BigDecimal remainingDebt = loanAccountMapper.findRemainingDebtTotal(loanAccountId)
                .orElseThrow(() -> new LoanAccountNotFoundException(loanAccountId));
        return remainingDebt.compareTo(BigDecimal.ZERO) == 0;
    }
}
