package com.repayment.domain.acceleration.service;

import com.repayment.domain.acceleration.dto.response.LoanAccelerationCarryOverResponse;
import com.repayment.domain.acceleration.mapper.LoanAccelerationMapper;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.global.code.RepaymentScheduleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 기한이익상실로 처음 바뀐 대출의 밀린 금액을 loan_acceleration으로 옮기고, 납입 완료가 아닌 회차를 모두 닫는다.
 * 처음 전환하는 날에만 부르는 것은 부르는 쪽이 정한다. 이미 전환한 대출이면 아무것도 하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AccelerationConversionService {

    private final LoanAccelerationMapper loanAccelerationMapper;
    private final RepaymentScheduleMapper repaymentScheduleMapper;

    @Transactional
    public void convert(Long loanAccountId, LocalDate businessDate) {
        if (loanAccelerationMapper.existsByLoanAccountId(loanAccountId)) {
            return;
        }
        LoanAccelerationCarryOverResponse carryOver = loanAccelerationMapper.findCarryOver(loanAccountId);
        loanAccelerationMapper.insertAcceleration(loanAccountId, businessDate, carryOver);
        repaymentScheduleMapper.updateUnpaidInstallmentStatus(loanAccountId, RepaymentScheduleStatus.CLOSED_BY_ACCELERATION);
    }
}
