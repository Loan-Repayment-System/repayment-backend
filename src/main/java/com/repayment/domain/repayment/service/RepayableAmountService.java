package com.repayment.domain.repayment.service;

import com.repayment.domain.acceleration.exception.LoanAccelerationNotFoundException;
import com.repayment.domain.maturity.exception.LoanMaturityOverdueNotFoundException;
import com.repayment.domain.repayment.dto.response.RepayableAmountResponse;
import com.repayment.domain.repayment.dto.response.RepaymentTargetResponse;
import com.repayment.domain.repayment.dto.response.UnpaidAccelerationResponse;
import com.repayment.domain.repayment.dto.response.UnpaidDailyChargeResponse;
import com.repayment.domain.repayment.dto.response.UnpaidInstallmentResponse;
import com.repayment.domain.repayment.dto.response.UnpaidMaturityOverdueResponse;
import com.repayment.domain.repayment.mapper.RepaymentTargetMapper;
import com.repayment.global.code.LoanJudgmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.repayment.domain.repayment.service.RepaymentTargetType.*;

/**
 * 가장 최근 판정에 따라 지금 갚을 수 있는 대상을 충당 순서로 정렬하고 그 합을 계산한다.
 * 대상마다 충당 순서(repaymentOrder)를 1부터 매긴다.
 * 남은 금액이 0원인 대상은 넣지 않는다. 아무것도 바꾸지 않는다.
 */
@Service
@RequiredArgsConstructor
public class RepayableAmountService {

    private final RepaymentTargetMapper repaymentTargetMapper;

    @Transactional(readOnly = true)
    public RepayableAmountResponse calculate(Long loanAccountId, LoanJudgmentStatus latestJudgment) {
        List<RepaymentTargetResponse> targets = new ArrayList<>();
        switch (latestJudgment) {
            case NORMAL, BULLET_INTEREST_OVERDUE, INSTALLMENT_OVERDUE -> addInstallmentTargets(targets, loanAccountId);
            case BULLET_ACCELERATION, BULLET_ACCELERATION_CLAUSE_6,
                 INSTALLMENT_ACCELERATION, INSTALLMENT_ACCELERATION_CLAUSE_6 ->
                    addAccelerationTargets(targets, loanAccountId);
            case MATURITY_OVERDUE -> addMaturityOverdueTargets(targets, loanAccountId);
            case PAID_OFF -> {
            }
        }
        BigDecimal totalAmount = targets.stream()
                .map(RepaymentTargetResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RepayableAmountResponse(totalAmount, targets);
    }

    // 연체금(오래된 날짜부터) → '연체'·'납부 기한 도래' 회차를 예정일 순으로, 회차 안에서는 이자 → 원금
    private void addInstallmentTargets(List<RepaymentTargetResponse> targets, Long loanAccountId) {
        for (UnpaidDailyChargeResponse charge : repaymentTargetMapper.findUnpaidInstallmentDelinquencyCharges(loanAccountId)) {
            add(targets, INSTALLMENT_DELINQUENCY_CHARGE, charge.getChargeId(), charge.getUnpaidDelinquencyCharge());
        }
        for (UnpaidInstallmentResponse installment : repaymentTargetMapper.findUnpaidInstallments(loanAccountId)) {
            add(targets, INSTALLMENT_INTEREST, installment.getRepaymentScheduleId(), installment.getUnpaidInterest());
            add(targets, INSTALLMENT_PRINCIPAL, installment.getRepaymentScheduleId(), installment.getUnpaidPrincipal());
        }
    }

    // 연체금: 옮긴 연체금 → 하루 연체금 / 이자: 옮긴 이자 → 하루 약정이자 / 원금: 옮긴 밀린 원금 → 나머지 원금
    private void addAccelerationTargets(List<RepaymentTargetResponse> targets, Long loanAccountId) {
        UnpaidAccelerationResponse acceleration = repaymentTargetMapper.findUnpaidAcceleration(loanAccountId)
                .orElseThrow(() -> new LoanAccelerationNotFoundException(loanAccountId));
        Long accelerationId = acceleration.getLoanAccelerationId();
        List<UnpaidDailyChargeResponse> dailyCharges =
                repaymentTargetMapper.findUnpaidAccelerationDailyCharges(accelerationId);

        add(targets, ACCELERATION_CARRIED_DELINQUENCY_CHARGE, accelerationId,
                acceleration.getUnpaidCarriedDelinquencyCharge());
        for (UnpaidDailyChargeResponse charge : dailyCharges) {
            add(targets, ACCELERATION_DAILY_DELINQUENCY_CHARGE, charge.getChargeId(), charge.getUnpaidDelinquencyCharge());
        }
        add(targets, ACCELERATION_CARRIED_INTEREST, accelerationId, acceleration.getUnpaidCarriedInterest());
        for (UnpaidDailyChargeResponse charge : dailyCharges) {
            add(targets, ACCELERATION_DAILY_INTEREST, charge.getChargeId(), charge.getUnpaidInterest());
        }
        // 옮긴 밀린 원금은 원금잔액에 들어 있으므로, 나머지 원금은 원금잔액에서 그만큼 뺀다.
        add(targets, ACCELERATION_CARRIED_OVERDUE_PRINCIPAL, accelerationId,
                acceleration.getUnpaidCarriedOverduePrincipal());
        add(targets, ACCELERATION_REMAINING_PRINCIPAL, loanAccountId,
                acceleration.getLoanPrincipalBalance().subtract(acceleration.getUnpaidCarriedOverduePrincipal()));
    }

    // 연체금: 옮긴 연체금 → 하루 연체금 / 이자: 옮긴 이자 / 원금: 원금잔액
    private void addMaturityOverdueTargets(List<RepaymentTargetResponse> targets, Long loanAccountId) {
        UnpaidMaturityOverdueResponse maturityOverdue = repaymentTargetMapper.findUnpaidMaturityOverdue(loanAccountId)
                .orElseThrow(() -> new LoanMaturityOverdueNotFoundException(loanAccountId));
        Long maturityOverdueId = maturityOverdue.getLoanMaturityOverdueId();

        add(targets, MATURITY_OVERDUE_CARRIED_DELINQUENCY_CHARGE, maturityOverdueId,
                maturityOverdue.getUnpaidCarriedDelinquencyCharge());
        for (UnpaidDailyChargeResponse charge : repaymentTargetMapper.findUnpaidMaturityOverdueDailyCharges(maturityOverdueId)) {
            add(targets, MATURITY_OVERDUE_DAILY_DELINQUENCY_CHARGE, charge.getChargeId(), charge.getUnpaidDelinquencyCharge());
        }
        add(targets, MATURITY_OVERDUE_CARRIED_INTEREST, maturityOverdueId, maturityOverdue.getUnpaidCarriedInterest());
        add(targets, MATURITY_OVERDUE_PRINCIPAL, loanAccountId, maturityOverdue.getLoanPrincipalBalance());
    }

    private void add(List<RepaymentTargetResponse> targets, RepaymentTargetType type, Long targetId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            targets.add(new RepaymentTargetResponse(targets.size() + 1, type, targetId, amount));
        }
    }
}
