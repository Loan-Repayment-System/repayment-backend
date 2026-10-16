package com.repayment.domain.repayment.service;

import com.repayment.domain.delinquency.exception.InstallmentDelinquencyChargeNotFoundException;
import com.repayment.domain.delinquency.mapper.InstallmentDelinquencyChargeMapper;
import com.repayment.domain.loan.mapper.LoanAccountMapper;
import com.repayment.domain.repayment.dto.response.InstallmentRepaymentResponse;
import com.repayment.domain.repayment.dto.response.RepaymentTargetResponse;
import com.repayment.domain.repayment.exception.RepaymentAmountLeftOverException;
import com.repayment.domain.repayment.exception.RepaymentTargetTypeMismatchException;
import com.repayment.domain.repayment.mapper.InstallmentRepaymentMapper;
import com.repayment.domain.schedule.mapper.RepaymentScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.repayment.domain.repayment.service.RepaymentTargetType.*;

/**
 * 정상·연체 대출(0·11·21)의 납부 금액을 갚을 금액 목록의 위에서부터 충당한다.
 * 목록은 상환 가능 확인이 돌려준 것이고, 받은 순서와 상관없이 충당 순서(repaymentOrder)대로 채운다.
 * 대상의 남은 금액보다 남은 납부 금액이 적으면 그 대상에서 멈춘다.
 * 충당 상세(installment_repayment)는 채운 회차마다 1행이고, 연체금은 그 회차 행에 합쳐 적는다.
 */
@Service
@RequiredArgsConstructor
public class InstallmentAllocationService {

    private static final Set<RepaymentTargetType> INSTALLMENT_TYPES =
            EnumSet.of(INSTALLMENT_DELINQUENCY_CHARGE, INSTALLMENT_INTEREST, INSTALLMENT_PRINCIPAL);

    private final InstallmentDelinquencyChargeMapper installmentDelinquencyChargeMapper;
    private final RepaymentScheduleMapper repaymentScheduleMapper;
    private final LoanAccountMapper loanAccountMapper;
    private final InstallmentRepaymentMapper installmentRepaymentMapper;

    @Transactional
    public void allocate(Long repaymentId, Long loanAccountId, BigDecimal paidAmount,
                         List<RepaymentTargetResponse> targets) {
        for (RepaymentTargetResponse target : targets) {
            if (!INSTALLMENT_TYPES.contains(target.getType())) {
                throw new RepaymentTargetTypeMismatchException(loanAccountId, target.getType());
            }
        }

        // 회차 id → 이 상환이 그 회차에 채운 금액. 처음 채운 순서대로 상세 행을 넣으려고 LinkedHashMap을 쓴다.
        Map<Long, Allocated> allocatedBySchedule = new LinkedHashMap<>();
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
                case INSTALLMENT_DELINQUENCY_CHARGE -> {
                    Long chargeId = target.getTargetId();
                    Long scheduleId = installmentDelinquencyChargeMapper.findRepaymentScheduleId(chargeId)
                            .orElseThrow(() -> new InstallmentDelinquencyChargeNotFoundException(chargeId));
                    installmentDelinquencyChargeMapper.updateAddPaidDelinquencyCharge(chargeId, fill);
                    allocatedOf(allocatedBySchedule, scheduleId).addDelinquencyCharge(fill);
                }
                case INSTALLMENT_INTEREST -> {
                    repaymentScheduleMapper.updateAddPaidInterest(target.getTargetId(), fill);
                    allocatedOf(allocatedBySchedule, target.getTargetId()).addInterest(fill);
                }
                case INSTALLMENT_PRINCIPAL -> {
                    repaymentScheduleMapper.updateAddPaidPrincipal(target.getTargetId(), fill);
                    allocatedOf(allocatedBySchedule, target.getTargetId()).addPrincipal(fill);
                    principalTotal = principalTotal.add(fill);
                }
                // 회차 방식 종류만 들어오도록 위에서 확인했다.
                default -> throw new RepaymentTargetTypeMismatchException(loanAccountId, target.getType());
            }
            remaining = remaining.subtract(fill);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new RepaymentAmountLeftOverException(loanAccountId, remaining);
        }

        if (principalTotal.compareTo(BigDecimal.ZERO) > 0) {
            loanAccountMapper.updateSubtractLoanPrincipalBalance(loanAccountId, principalTotal);
        }
        allocatedBySchedule.forEach((scheduleId, allocated) ->
                installmentRepaymentMapper.insertInstallmentRepayment(InstallmentRepaymentResponse.builder()
                        .repaymentId(repaymentId)
                        .repaymentScheduleId(scheduleId)
                        .paidPrincipal(allocated.principal)
                        .paidInterest(allocated.interest)
                        .paidDelinquencyCharge(allocated.delinquencyCharge)
                        .build()));
    }

    private Allocated allocatedOf(Map<Long, Allocated> allocatedBySchedule, Long scheduleId) {
        return allocatedBySchedule.computeIfAbsent(scheduleId, id -> new Allocated());
    }

    // 상환 1건이 회차 하나에 채운 금액을 모은다.
    private static class Allocated {
        private BigDecimal principal = BigDecimal.ZERO;
        private BigDecimal interest = BigDecimal.ZERO;
        private BigDecimal delinquencyCharge = BigDecimal.ZERO;

        private void addPrincipal(BigDecimal amount) {
            principal = principal.add(amount);
        }

        private void addInterest(BigDecimal amount) {
            interest = interest.add(amount);
        }

        private void addDelinquencyCharge(BigDecimal amount) {
            delinquencyCharge = delinquencyCharge.add(amount);
        }
    }
}
