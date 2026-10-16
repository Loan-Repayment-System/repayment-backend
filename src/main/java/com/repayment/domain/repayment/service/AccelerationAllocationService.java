package com.repayment.domain.repayment.service;

import com.repayment.domain.acceleration.mapper.AccelerationDailyChargeMapper;
import com.repayment.domain.acceleration.mapper.LoanAccelerationMapper;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.repayment.dto.response.RepaymentAllocationResponse;
import com.repayment.domain.repayment.dto.response.RepaymentTargetResponse;
import com.repayment.domain.repayment.exception.RepaymentAmountLeftOverException;
import com.repayment.domain.repayment.exception.RepaymentTargetTypeMismatchException;
import com.repayment.domain.repayment.mapper.RepaymentAllocationMapper;
import com.repayment.global.code.RepaymentTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.repayment.global.code.RepaymentTargetType.*;

/**
 * 기한이익상실 대출(12·13·22·23)의 납부 금액을 갚을 금액 목록의 충당 순서(repaymentOrder)대로 충당한다.
 * 목록은 상환 가능 확인이 돌려준 것이다. 대상의 남은 금액보다 남은 납부 금액이 적으면 그 대상에서 멈춘다.
 * 금액만 더하고 상태는 바꾸지 않는다. 충당한 대상마다 충당 상세(repayment_allocation)를 1행 남긴다.
 */
@Service
@RequiredArgsConstructor
public class AccelerationAllocationService {

    private static final Set<RepaymentTargetType> ACCELERATION_TYPES = EnumSet.of(
            ACCELERATION_CARRIED_DELINQUENCY_CHARGE, ACCELERATION_DAILY_DELINQUENCY_CHARGE,
            ACCELERATION_CARRIED_INTEREST, ACCELERATION_DAILY_INTEREST,
            ACCELERATION_CARRIED_OVERDUE_PRINCIPAL, ACCELERATION_REMAINING_PRINCIPAL);

    private final LoanAccelerationMapper loanAccelerationMapper;
    private final AccelerationDailyChargeMapper accelerationDailyChargeMapper;
    private final LoanAccountMapper loanAccountMapper;
    private final RepaymentAllocationMapper repaymentAllocationMapper;

    @Transactional
    public void allocate(Long repaymentId, Long loanAccountId, BigDecimal paidAmount,
                         List<RepaymentTargetResponse> targets) {
        for (RepaymentTargetResponse target : targets) {
            if (!ACCELERATION_TYPES.contains(target.getType())) {
                throw new RepaymentTargetTypeMismatchException(loanAccountId, target.getType());
            }
        }

        BigDecimal principalTotal = BigDecimal.ZERO;
        BigDecimal remaining = paidAmount;
        List<RepaymentTargetResponse> orderedTargets = targets.stream()
                .sorted(Comparator.comparingInt(RepaymentTargetResponse::getRepaymentOrder))
                .toList();
        for (RepaymentTargetResponse target : orderedTargets) {
            if (remaining.compareTo(BigDecimal.ZERO) == 0) {
                break;
            }
            BigDecimal fill = remaining.min(target.getAmount());
            switch (target.getType()) {
                case ACCELERATION_CARRIED_DELINQUENCY_CHARGE ->
                        loanAccelerationMapper.updateAddPaidCarriedDelinquencyCharge(target.getTargetId(), fill);
                case ACCELERATION_DAILY_DELINQUENCY_CHARGE ->
                        accelerationDailyChargeMapper.updateAddPaidDelinquencyCharge(target.getTargetId(), fill);
                case ACCELERATION_CARRIED_INTEREST ->
                        loanAccelerationMapper.updateAddPaidCarriedInterest(target.getTargetId(), fill);
                case ACCELERATION_DAILY_INTEREST ->
                        accelerationDailyChargeMapper.updateAddPaidInterest(target.getTargetId(), fill);
                case ACCELERATION_CARRIED_OVERDUE_PRINCIPAL -> {
                    loanAccelerationMapper.updateAddPaidCarriedOverduePrincipal(target.getTargetId(), fill);
                    principalTotal = principalTotal.add(fill);
                }
                // 나머지 원금은 원금잔액에서만 뺀다. 옮긴 밀린 원금도 원금잔액에 들어 있어 함께 뺀다.
                case ACCELERATION_REMAINING_PRINCIPAL -> principalTotal = principalTotal.add(fill);
                // 기한이익상실 방식 종류만 들어오도록 위에서 확인했다.
                default -> throw new RepaymentTargetTypeMismatchException(loanAccountId, target.getType());
            }
            repaymentAllocationMapper.insertAllocation(RepaymentAllocationResponse.builder()
                    .repaymentId(repaymentId)
                    .repaymentTargetTypeId(target.getType())
                    .targetId(target.getTargetId())
                    .paidAmount(fill)
                    .build());
            remaining = remaining.subtract(fill);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new RepaymentAmountLeftOverException(loanAccountId, remaining);
        }

        if (principalTotal.compareTo(BigDecimal.ZERO) > 0) {
            loanAccountMapper.updateSubtractLoanPrincipalBalance(loanAccountId, principalTotal);
        }
    }
}
