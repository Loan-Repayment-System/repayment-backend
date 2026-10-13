package com.repayment.domain.schedule.service;

import com.repayment.domain.schedule.dto.response.AppliedRateResponse;
import com.repayment.domain.schedule.dto.response.RepaymentAmountResponse;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleConfirmTargetResponse;
import com.repayment.domain.schedule.exception.InstallmentConfirmOrderException;
import com.repayment.domain.schedule.exception.RepaymentScheduleNotFoundException;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.global.code.RepaymentScheduleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 회차 하나의 상환금(원금·이자)을 확정한다. 언제 확정할지는 대출 스케줄 판정이 정한다.
 */
@Service
@RequiredArgsConstructor
public class RepaymentScheduleConfirmService {

    private final RepaymentScheduleMapper repaymentScheduleMapper;
    private final AppliedRateService appliedRateService;
    private final RepaymentAmountCalculator repaymentAmountCalculator;

    @Transactional
    public void confirm(Long repaymentScheduleId) {
        RepaymentScheduleConfirmTargetResponse target = repaymentScheduleMapper.findConfirmTarget(repaymentScheduleId)
                .orElseThrow(() -> new RepaymentScheduleNotFoundException(repaymentScheduleId));

        // 계산 잔액이 앞 회차 원금에 기대므로 회차 번호 순서대로만 확정한다
        int installmentNo = target.getInstallmentNo();
        int lastConfirmedInstallmentNo = target.getLastConfirmedInstallmentNo();
        if (installmentNo != lastConfirmedInstallmentNo + 1) {
            throw new InstallmentConfirmOrderException(
                    target.getLoanAccountId(), installmentNo, lastConfirmedInstallmentNo);
        }

        AppliedRateResponse rate = appliedRateService.resolve(target.getInterestTypeId(),
                target.getContractBaseRate(), target.getContractAdditionalRate(), target.getScheduledDate());

        BigDecimal balance = target.getContractPrincipal().subtract(target.getPriorScheduledPrincipalSum());
        int remainingInstallments = target.getTotalInstallments() - installmentNo + 1;
        RepaymentAmountResponse amount = repaymentAmountCalculator.calculate(target.getRepaymentMethodId(), balance,
                rate.getBaseRate().add(rate.getAdditionalRate()), remainingInstallments,
                target.getContractPrincipal(), target.getTotalInstallments());

        RepaymentScheduleStatus status = amount.getPrincipal().add(amount.getInterest()).signum() == 0
                ? RepaymentScheduleStatus.PAID
                : RepaymentScheduleStatus.DUE;
        repaymentScheduleMapper.updateConfirmation(repaymentScheduleId, amount, rate, status);
        repaymentScheduleMapper.updateLastConfirmedInstallmentNo(target.getLoanAccountId(), installmentNo);
    }
}
