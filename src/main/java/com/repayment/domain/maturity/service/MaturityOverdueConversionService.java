package com.repayment.domain.maturity.service;

import com.repayment.domain.maturity.dto.response.LoanMaturityOverdueCarryOverResponse;
import com.repayment.domain.maturity.mapper.LoanMaturityOverdueMapper;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.global.code.RepaymentScheduleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 만기 경과로 처음 바뀐 대출의 밀린 이자·연체금을 loan_maturity_overdue로 옮기고, 납입 완료가 아닌 회차를 모두 닫는다.
 * 처음 전환하는 날에만 부르는 것은 부르는 쪽이 정한다. 이미 전환한 대출이면 아무것도 하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MaturityOverdueConversionService {

    private final LoanMaturityOverdueMapper loanMaturityOverdueMapper;
    private final RepaymentScheduleMapper repaymentScheduleMapper;

    @Transactional
    public void convert(Long loanAccountId, LocalDate businessDate) {
        if (loanMaturityOverdueMapper.existsByLoanAccountId(loanAccountId)) {
            return;
        }
        LoanMaturityOverdueCarryOverResponse carryOver = loanMaturityOverdueMapper.findCarryOver(loanAccountId);
        loanMaturityOverdueMapper.insertMaturityOverdue(loanAccountId, businessDate, carryOver);
        repaymentScheduleMapper.updateUnpaidInstallmentStatus(loanAccountId, RepaymentScheduleStatus.CLOSED_BY_MATURITY_OVERDUE);
    }
}
