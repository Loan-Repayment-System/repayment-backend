package com.repayment.domain.delinquency.service;

import com.repayment.domain.delinquency.dto.response.InstallmentDelinquencyChargeResponse;
import com.repayment.domain.delinquency.dto.response.InstallmentDelinquencyChargeTargetResponse;
import com.repayment.domain.delinquency.exception.InstallmentDelinquencyNotApplicableException;
import com.repayment.domain.delinquency.mapper.InstallmentDelinquencyChargeMapper;
import com.repayment.global.code.DelinquencyStatus;
import com.repayment.global.code.LoanJudgmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * 오늘 판정이 11·21인 대출의 '연체' 회차마다 그날 연체금을 1행 쌓는다.
 * 오늘 판정은 부르는 쪽이 넘긴다. 그날 이미 쌓은 회차는 조회에서 빠진다.
 */
@Service
@RequiredArgsConstructor
public class InstallmentDelinquencyChargeService {

    private static final Set<LoanJudgmentStatus> ACCRUABLE_JUDGMENTS = EnumSet.of(
            LoanJudgmentStatus.BULLET_INTEREST_OVERDUE,
            LoanJudgmentStatus.INSTALLMENT_OVERDUE);

    private final InstallmentDelinquencyChargeMapper installmentDelinquencyChargeMapper;
    private final DelinquencyChargeCalculator delinquencyChargeCalculator;

    @Transactional
    public void accrue(Long loanAccountId, LocalDate businessDate, LoanJudgmentStatus todayJudgment) {
        if (!ACCRUABLE_JUDGMENTS.contains(todayJudgment)) {
            throw new InstallmentDelinquencyNotApplicableException(loanAccountId, todayJudgment);
        }
        for (InstallmentDelinquencyChargeTargetResponse target
                : installmentDelinquencyChargeMapper.findAccrualTargets(loanAccountId, businessDate)) {
            installmentDelinquencyChargeMapper.insertCharge(charge(target, businessDate, todayJudgment));
        }
    }

    private InstallmentDelinquencyChargeResponse charge(InstallmentDelinquencyChargeTargetResponse target,
                                                        LocalDate businessDate, LoanJudgmentStatus todayJudgment) {
        BigDecimal targetAmount = targetAmount(target, todayJudgment);
        BigDecimal loanRate = target.getAppliedBaseRate().add(target.getAppliedAdditionalRate());
        BigDecimal delinquencyRate = delinquencyChargeCalculator.calculateDelinquencyRate(loanRate);
        BigDecimal delinquencyCharge = delinquencyChargeCalculator.calculateDailyCharge(targetAmount, delinquencyRate);
        return InstallmentDelinquencyChargeResponse.builder()
                .repaymentScheduleId(target.getRepaymentScheduleId())
                .chargeDate(businessDate)
                .loanJudgmentStatusId(todayJudgment)
                .delinquencyTargetAmount(targetAmount)
                .delinquencyRate(delinquencyRate)
                .delinquencyCharge(delinquencyCharge)
                .delinquencyStatusId(delinquencyCharge.compareTo(BigDecimal.ZERO) == 0
                        ? DelinquencyStatus.RESOLVED
                        : DelinquencyStatus.IN_PROGRESS)
                .build();
    }

    /**
     * 11 만기일시 이자연체는 남은 이자, 21 분할상환 1회 연체는 남은 원금+이자에 연체금이 붙는다.
     */
    private BigDecimal targetAmount(InstallmentDelinquencyChargeTargetResponse target, LoanJudgmentStatus todayJudgment) {
        BigDecimal remainingInterest = target.getScheduledInterest().subtract(target.getPaidInterest());
        if (todayJudgment == LoanJudgmentStatus.BULLET_INTEREST_OVERDUE) {
            return remainingInterest;
        }
        return remainingInterest.add(target.getScheduledPrincipal().subtract(target.getPaidPrincipal()));
    }
}
