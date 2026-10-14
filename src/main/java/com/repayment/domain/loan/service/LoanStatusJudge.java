package com.repayment.domain.loan.service;

import com.repayment.domain.loan.dto.response.LoanStatusJudgmentTargetResponse;
import com.repayment.global.code.LoanJudgmentStatus;
import com.repayment.global.code.RepaymentMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * 대출 하나의 판정할 값과 기준일만 보고 판정 상태(LoanJudgmentStatus)를 정한다.
 */
@Component
public class LoanStatusJudge {

    // ❻항: 약정금액이 이 금액 미만이면 적용
    private static final BigDecimal DEBTOR_PROTECTION_PRINCIPAL_LIMIT = new BigDecimal("50000000");
    private static final Set<LoanJudgmentStatus> ACCELERATION_STATUSES = EnumSet.of(
            LoanJudgmentStatus.BULLET_ACCELERATION,
            LoanJudgmentStatus.BULLET_ACCELERATION_CLAUSE_6,
            LoanJudgmentStatus.INSTALLMENT_ACCELERATION,
            LoanJudgmentStatus.INSTALLMENT_ACCELERATION_CLAUSE_6);

    public LoanJudgmentStatus judge(LoanStatusJudgmentTargetResponse target, boolean paidOff, LocalDate businessDate) {
        if (paidOff) {
            return LoanJudgmentStatus.PAID_OFF;
        }
        // 기한이익상실·만기 경과는 회차가 닫혀 대출 단위로 옮겨졌으므로 회차를 보지 않는다
        if (ACCELERATION_STATUSES.contains(target.getPreviousJudgmentStatus())) {
            return acceleration(target, businessDate);
        }
        if (target.getPreviousJudgmentStatus() == LoanJudgmentStatus.MATURITY_OVERDUE) {
            return LoanJudgmentStatus.MATURITY_OVERDUE;
        }
        return judgeByInstallments(target, businessDate);
    }

    /**
     * 직전 판정이 0 정상·11 이자연체·21 1회 연체일 때 밀린 회차로 판정한다.
     * 만기가 지났으면 상환 방법과 상관없이 1 만기 경과다.
     */
    private LoanJudgmentStatus judgeByInstallments(LoanStatusJudgmentTargetResponse target, LocalDate businessDate) {
        if (target.getOverdueInstallmentCount() == 0) {
            return LoanJudgmentStatus.NORMAL;
        }
        if (businessDate.isAfter(target.getMaturityDate())) {
            return LoanJudgmentStatus.MATURITY_OVERDUE;
        }
        if (target.getRepaymentMethodId() == RepaymentMethod.BULLET) {
            return judgeBullet(target, businessDate);
        }
        return judgeInstallment(target, businessDate);
    }

    /**
     * 만기일시: 가장 오래 밀린 예정일부터 1개월까지는 이자연체, 그 뒤로는 기한이익상실이다.
     */
    private LoanJudgmentStatus judgeBullet(LoanStatusJudgmentTargetResponse target, LocalDate businessDate) {
        // 같은 날이 없는 달은 그 달 말일까지다(1/31 + 1개월 = 2/28)
        LocalDate lastInterestOverdueDate = target.getOldestOverdueDate().plusMonths(1);
        if (!businessDate.isAfter(lastInterestOverdueDate)) {
            return LoanJudgmentStatus.BULLET_INTEREST_OVERDUE;
        }
        return acceleration(target, businessDate);
    }

    /**
     * 분할상환: 1회 밀리면 1회 연체, 2회 이상이면 기한이익상실이다.
     */
    private LoanJudgmentStatus judgeInstallment(LoanStatusJudgmentTargetResponse target, LocalDate businessDate) {
        if (target.getOverdueInstallmentCount() == 1) {
            return LoanJudgmentStatus.INSTALLMENT_OVERDUE;
        }
        return acceleration(target, businessDate);
    }

    /**
     * 기한이익상실은 상환 방법과 ❻항 적용 여부로 12·13·22·23 중 하나다.
     */
    private LoanJudgmentStatus acceleration(LoanStatusJudgmentTargetResponse target, LocalDate businessDate) {
        boolean debtorProtection = isDebtorProtectionApplied(target, businessDate);
        if (target.getRepaymentMethodId() == RepaymentMethod.BULLET) {
            return debtorProtection
                    ? LoanJudgmentStatus.BULLET_ACCELERATION_CLAUSE_6
                    : LoanJudgmentStatus.BULLET_ACCELERATION;
        }
        return debtorProtection
                ? LoanJudgmentStatus.INSTALLMENT_ACCELERATION_CLAUSE_6
                : LoanJudgmentStatus.INSTALLMENT_ACCELERATION;
    }

    /**
     * ❻항: 약정금액이 5천만 원 미만이고 만기 전(기준일 ≤ 만기일)이면 적용한다.
     */
    private boolean isDebtorProtectionApplied(LoanStatusJudgmentTargetResponse target, LocalDate businessDate) {
        return target.getContractPrincipal().compareTo(DEBTOR_PROTECTION_PRINCIPAL_LIMIT) < 0
                && !businessDate.isAfter(target.getMaturityDate());
    }
}
