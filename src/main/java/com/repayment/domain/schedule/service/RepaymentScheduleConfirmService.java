package com.repayment.domain.schedule.service;

import com.repayment.domain.schedule.dto.response.AppliedRateResponse;
import com.repayment.domain.schedule.dto.response.RepaymentAmountResponse;
import com.repayment.domain.schedule.dto.response.RepaymentScheduleConfirmTargetResponse;
import com.repayment.domain.schedule.exception.ConfirmDateOutOfRangeException;
import com.repayment.domain.schedule.exception.InstallmentConfirmOrderException;
import com.repayment.domain.schedule.exception.RepaymentScheduleNotConfirmableException;
import com.repayment.domain.schedule.exception.RepaymentScheduleNotFoundException;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import com.repayment.global.code.RepaymentScheduleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 회차 하나의 상환금(원금·이자)을 확정한다. 언제 확정할지와 확정 뒤 회차 상태는 대출 스케줄 판정이 정한다.
 */
@Service
@RequiredArgsConstructor
public class RepaymentScheduleConfirmService {

    private static final int CONFIRM_DAYS_BEFORE_SCHEDULED_DATE = 5;

    private final RepaymentScheduleMapper repaymentScheduleMapper;
    private final AppliedRateService appliedRateService;
    private final RepaymentAmountCalculator repaymentAmountCalculator;

    /**
     * 기준일이 '예정일 − 5일' ~ 예정일 사이인 '예정' 회차만, 회차 번호 순서대로 확정하고 확정 금액을 돌려준다.
     */
    @Transactional
    public RepaymentAmountResponse confirm(Long repaymentScheduleId, LocalDate businessDate) {
        RepaymentScheduleConfirmTargetResponse target = repaymentScheduleMapper.findConfirmTarget(repaymentScheduleId)
                .orElseThrow(() -> new RepaymentScheduleNotFoundException(repaymentScheduleId));

        if (target.getRepaymentScheduleStatusId() != RepaymentScheduleStatus.SCHEDULED) {
            throw new RepaymentScheduleNotConfirmableException(
                    repaymentScheduleId, target.getRepaymentScheduleStatusId());
        }

        LocalDate scheduledDate = target.getScheduledDate();
        if (businessDate.isBefore(scheduledDate.minusDays(CONFIRM_DAYS_BEFORE_SCHEDULED_DATE))
                || businessDate.isAfter(scheduledDate)) {
            throw new ConfirmDateOutOfRangeException(repaymentScheduleId, businessDate, scheduledDate);
        }

        // 계산 잔액이 앞 회차 원금에 기대므로 회차 번호 순서대로만 확정한다
        int installmentNo = target.getInstallmentNo();
        int lastConfirmedInstallmentNo = target.getLastConfirmedInstallmentNo();
        if (installmentNo != lastConfirmedInstallmentNo + 1) {
            throw new InstallmentConfirmOrderException(
                    target.getLoanAccountId(), installmentNo, lastConfirmedInstallmentNo);
        }

        AppliedRateResponse rate = appliedRateService.resolve(target.getInterestTypeId(),
                target.getContractBaseRate(), target.getContractAdditionalRate(), scheduledDate);

        BigDecimal balance = target.getContractPrincipal().subtract(target.getPriorScheduledPrincipalSum());
        int remainingInstallments = target.getTotalInstallments() - installmentNo + 1;
        RepaymentAmountResponse amount = repaymentAmountCalculator.calculate(target.getRepaymentMethodId(), balance,
                rate.getBaseRate().add(rate.getAdditionalRate()), remainingInstallments,
                target.getContractPrincipal(), target.getTotalInstallments());

        repaymentScheduleMapper.updateConfirmation(repaymentScheduleId, amount, rate);
        repaymentScheduleMapper.updateLastConfirmedInstallmentNo(target.getLoanAccountId(), installmentNo);
        return amount;
    }
}
